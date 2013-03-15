import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.net.SocketException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteServer;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.umn.distributed.common.Command;
import org.umn.distributed.common.CommandNotParsedException;
import org.umn.distributed.common.Parameter;
import org.umn.distributed.common.Parameter.PARA_TYPE;
import org.umn.distributed.common.ParsedCommand;
import org.umn.distributed.common.Props;
import org.umn.distributed.common.Utils;
import org.umn.distributed.server.Article;
import org.umn.distributed.server.ClientMachine;
import org.umn.distributed.server.Machine;
import org.umn.distributed.server.MatcherUtils;

/**
 * <pre>
 * This class is the group server class. This class defines different RMI method for accepting and
 * publishing the article to clients and group servers. 
 * </pre>
 */
public class PublisherServer extends RemoteServer implements Communicate {
	private static final String SHUTDOWN_COMMAND = "shutdown";
	private static final String GETLIST_COMMAND = "getlist";
	private static final String JOINSERVER_COMAND = "joinserver";

	static {
		initCommands();
	}

	private int rmiPort;

	protected ConcurrentHashMap<String, ConcurrentHashMap<String, ConcurrentHashMap<String, Set<Machine>>>> clientSubscriptions = new ConcurrentHashMap<String, ConcurrentHashMap<String, ConcurrentHashMap<String, Set<Machine>>>>(
			8, 0.9f, 2);
	/**
	 * Need to establish a listener thread on this UDP port as this is
	 * advertised to the clients as well as the registry server and the other
	 * joined servers who send articles on this port
	 */
	private int udpPort;

	public int getUDPPort() {
		return udpPort;
	}

	private static final long serialVersionUID = 1L;
	private static final int MAX_MACHINES_ATTACHED = 10;
	private static final int MAX_THREADS = 10;
	// TODO: should be concurrenthashsets here.
	private Set<ClientMachine> attachedClients = new HashSet<ClientMachine>();
	private Set<ServerMachine> attachedServers = new HashSet<ServerMachine>();
	private ServerRegistryClient udpServerOperations = null;
	// TODO parameterize open port list
	private ArticleDispatcher artDispatcher = null;
	private CollectorAndMatcher collectorMatcher = null;
	private static Logger logger = Logger.getLogger(PublisherServer.class);
	private HashMap<ServerMachine, ServerMachine> getListKnownMachines = new HashMap<ServerMachine, ServerMachine>();
	private Object getListInvokerLock = new Object();
	private GetListProcessor getListProcessor;

	public PublisherServer(String port, String openPortList, int rmiPort,
			String registryServerHostname, int registryServerPort)
			throws SocketException {
		this.udpPort = Integer.parseInt(port);
		this.rmiPort = rmiPort;
		// this.registryServerHostname = registryServerHostname;
		// this.registryServerPort = registryServerPort;
		// TODO: change this fixed port
		udpServerOperations = new ServerRegistryClient(registryServerHostname,
				registryServerPort, Props.SERVER_UDP_PORT, this.udpPort,
				this.getListKnownMachines, this.attachedServers, this.rmiPort);
		// TODO: remove the localServer IP pass parameter
		artDispatcher = new ArticleDispatcher(MAX_THREADS,
				Collections.unmodifiableSet(attachedClients),
				Collections.unmodifiableSet(attachedServers), openPortList,
				Utils.getLocalServerIp(), this.rmiPort);
		collectorMatcher = new CollectorAndMatcher(artDispatcher,
				Collections.unmodifiableSet(attachedClients),
				Collections.unmodifiableSet(attachedServers),
				clientSubscriptions, MAX_THREADS);
		// TODO: make it configurable
		getListProcessor = new GetListProcessor(getListInvokerLock,
				Props.GETLIST_REQUEST_INTERVAL);
		getListProcessor.start();
	}

	@Override
	public synchronized boolean Join(String ip, int port) {
		ClientMachine machine = new ClientMachine(ip, port);
		logger.debug("Joining " + machine);
		if (attachedClients.size() + attachedServers.size() < MAX_MACHINES_ATTACHED) {
			if (attachedClients.contains(machine)) {
				logger.warn(machine + " already joined");
				return false;
			}
			attachedClients.add(new ClientMachine(ip, port));
			return true;
		}
		return false;
	}

	@Override
	public synchronized boolean Subscribe(String ip, int port,
			String articleString) {
		ClientMachine machine = new ClientMachine(ip, port);
		logger.debug(machine + " subscribing to " + articleString);
		if (!validateArticleSubscriptionFormat(articleString)) {
			return false;
		}
		Article article = new Article(articleString);

		if (isClientJoined(machine)) {
			clientSubscriptions
					.putIfAbsent(
							article.getArticleType().toString(),
							new ConcurrentHashMap<String, ConcurrentHashMap<String, Set<Machine>>>(
									8, 0.9f, 1));
			ConcurrentHashMap<String, ConcurrentHashMap<String, Set<Machine>>> map1 = clientSubscriptions
					.get(article.getArticleType().toString());
			map1.putIfAbsent(article.getArticleOrignator(),
					new ConcurrentHashMap<String, Set<Machine>>(8, 0.9f, 1));
			ConcurrentHashMap<String, Set<Machine>> map2 = map1.get(article
					.getArticleOrignator());
			map2.putIfAbsent(article.getArticleOrg(), new HashSet<Machine>());
			Set<Machine> existingMachineSet = new HashSet<Machine>();
			existingMachineSet.addAll(map2.get(article.getArticleOrg()));
			if (existingMachineSet.contains(machine)) {
				logger.warn(machine + " already subscribed to " + articleString);
				return false;
			}
			existingMachineSet.add(machine);
			map2.replace(article.getArticleOrg(), existingMachineSet);
			return true;
		} else {
			logger.warn(machine + " cannont subscribe without joining");
		}
		return false;
	}

	private synchronized boolean isClientJoined(Machine m) {
		return attachedClients.contains(m);
	}

	private synchronized boolean isServerJoined(Machine m) {
		return attachedServers.contains(m);
	}

	private boolean validateArticleSubscriptionFormat(String article) {
		Article a = new Article(article);
		if ((a.getArticleType() != null || a.getArticleOrg() != null || a
				.getArticleOrignator() != null)
				&& Utils.isEmpty(a.getArticleContent()))
			return true;
		return false;
	}

	/**
	 * TODO what if we have existing articles in queue for this client, in the
	 * distribution framework just before sending out the document we can check
	 * if client is still subscribed to the article or not. It should be easy to
	 * check as we have the subscription details of client and the article
	 * details, that is we do not need to access the matching logic again. But
	 * this is taxing the system, a better system would be to a set of clients
	 * where we input the client if the subscription changed in the last x
	 * minutes (or we can note the article id at the end of the distribuition
	 * queue, once that article is out we remove this client, but what if again
	 * the subscription changed, in that case we would renew that article id and
	 * for each article that is exiting we can add a set of clients to check, so
	 * in 2 hashset comparison we will come to know if we need to check
	 * subscription on some client)
	 */
	// TODO: cannot unscubscribe without subscribing
	@Override
	public synchronized boolean Unsubscribe(String ip, int port, String article) {
		logger.debug("TODO,Unsubscribe ip=" + ip + "<< port=" + port
				+ "<< articleString=" + article + "<<");
		/*
		 * clientSubscriptions remove from this map so we need to break the
		 * article into its components
		 */
		if (!validateArticleSubscriptionFormat(article)) {
			logger.error("Article format not valid for unsubscription, article="
					+ article);
			return false;
		}
		Article artToMatch2 = new Article(article);

		List<ConcurrentHashMap<String, ConcurrentHashMap<String, Set<Machine>>>> firstLevelMatches = MatcherUtils
				.getFirstLevelMatches(artToMatch2, this.clientSubscriptions,
						false);
		if (firstLevelMatches.size() > 0) {
			List<ConcurrentHashMap<String, Set<Machine>>> secondLevelMatches = new LinkedList<ConcurrentHashMap<String, Set<Machine>>>();
			for (ConcurrentHashMap<String, ConcurrentHashMap<String, Set<Machine>>> match : firstLevelMatches) {
				if (match != null) {
					secondLevelMatches.addAll(MatcherUtils
							.getSecondLevelMatches(artToMatch2, match, false));
				}
			}
			if (secondLevelMatches.size() > 0) {
				Machine toRemove = new ClientMachine(ip, port);
				for (ConcurrentHashMap<String, Set<Machine>> match2 : secondLevelMatches) {
					if (match2 != null) {
						if (artToMatch2.getArticleOrg() != null
								&& match2.containsKey(artToMatch2
										.getArticleOrg())) {
							// need to update the set if it contains the third
							// level matches
							Set<Machine> setWhichContainsMachineToRemove = match2
									.get(artToMatch2.getArticleOrg());
							setWhichContainsMachineToRemove.remove(toRemove);
							match2.replace(artToMatch2.getArticleOrg(),
									setWhichContainsMachineToRemove);
							return true;
						}
					}
				}

			}
		}

		return false;
	}

	/**
	 * Should be relatively simple
	 */
	@Override
	public boolean Publish(String article, String ip, int port)
			throws RemoteException {
		logger.debug("Publishing client article =" + article);
		return publishArticle(article, ip, port, true);
	}

	private boolean publishArticle(String article, String ip, int port,
			boolean publishFromClient) throws RemoteException {
		Article a = null;
		if (publishFromClient) {
			ClientMachine machine = new ClientMachine(ip, port);
			logger.debug(machine + " trying to publish article:" + article);
			try {
				a = new Article(article);
			} catch (Exception e) {
				logger.error(machine
						+ " published article with invalid format: " + article,e);
				return false;
			}
			if (isClientJoined(machine)) {
				collectorMatcher.publishClient(a, machine);
				collectorMatcher.pubServers(a, machine);
				return true;
			} else {
				logger.warn(machine + " not joined");
			}
		} else {
			ServerMachine machine = new ServerMachine(ip, port, null, null);
			logger.debug(machine + " trying to publish article:" + article);
			try {
				a = new Article(article);
			} catch (Exception e) {
				logger.error(machine
						+ " published article with invalid format: " + article, e);
				return false;
			}
			if (isServerJoined(machine)) {
				collectorMatcher.publishClient(a, machine);
				return true;
			} else {
				logger.warn(machine + " not joined");
			}
		}
		return false;
	}

	// public static String getClientHost() throws ServerNotActiveException {
	// if (test) {
	// Random r = new Random();
	// return "client:" + r.nextInt(4) + ";0";
	// } else {
	// return RemoteServer.getClientHost();
	// }
	// }

	/**
	 * return true if health check is true
	 */
	@Override
	public boolean Ping() {
		logger.info("Time server pinged is :" + System.currentTimeMillis());
		return true;
	}

	@Override
	public synchronized boolean JoinServer(String ip, int port)
			throws RemoteException {
		ServerMachine machine = new ServerMachine(ip, port, null, null);
		logger.debug(machine + " trying to join");
		if (attachedClients.size() + attachedServers.size() < MAX_MACHINES_ATTACHED) {
			if (!attachedServers.contains(machine)) {
				if (!this.getListKnownMachines.containsKey(machine)) {
					udpServerOperations.getList();
				} else {
					machine = this.getListKnownMachines.get(machine);
				}
				attachedServers.add(machine);
				logger.info(machine + " joined successfully");
				return true;
			} else {
				logger.warn(machine + " already joined");
			}
		} else {
			logger.warn(machine + " MAX_MACHINES_ATTACHED="
					+ MAX_MACHINES_ATTACHED + " limit reached");
		}
		return false;
	}

	// /**
	// * TODO this is a test method, in reality the server will not create
	// * articles but articles will come from the clients and the joined servers
	// * and these will be published to all the other clients and also joined
	// * servers, the servers will not propagate this second hand publishing to
	// * stop the flow. Now as this method emulates the articles being published
	// * from the client we will publish this to joined servers as well
	// *
	// * @param pubSub
	// * @param numberOfArticles
	// * @throws RemoteException
	// */
	// private static void createArticles(PublisherServer pubSub,
	// int numberOfArticles) throws RemoteException {
	// String[] articleType = { "sports", "lifestyle" };
	// String[] articleOrig = { "Orig1", "Orig2" };
	// String[] articleOrg = { "OrgA", "OrgB" };
	// String articleContent = "content";
	// Random r = new Random();
	// for (int i = 0; i < numberOfArticles; i++) {
	// int idx1 = r.nextInt(2);
	// int idx2 = r.nextInt(2);
	// int idx3 = r.nextInt(2);
	// pubSub.Publish(String.format("%s;%s;%s;%s", articleType[idx1],
	// articleOrig[idx2], articleOrg[idx3], articleContent),
	// "client:" + r.nextInt(4), 0);
	// pubSub.PublishServer(String.format("%s;%s;%s;%s",
	// articleType[idx1], articleOrig[idx2], articleOrg[idx3],
	// articleContent), "client:" + r.nextInt(4), 0);
	// }
	//
	// }

	// private static void createClientsAndSubscribe(PublisherServer pubSub,
	// int numberOfClients) {
	// for (int i = 0; i < numberOfClients; i++) {
	// ClientMachine m = new ClientMachine("client:" + i + ";0",
	// Machine.TYPE_LOCALCLIENT);
	// String subArt = null;
	//
	// if (i % 10 == 0) {
	// subArt = "sports;ORIG1;ORGA;";
	// } else if (i % 9 == 0) {
	// subArt = "lifestyle;ORG1;ORGA;";
	// } else if (i % 8 == 0) {
	// subArt = "sports;ORIG2;ORGA;";
	// } else if (i % 7 == 0) {
	// subArt = "sports;ORIG2;ORGB;";
	// } else if (i % 6 == 0) {
	// subArt = "sports;;;";
	// } else if (i % 5 == 0) {
	// subArt = "lifestyle;;ORGA;";
	// } else if (i % 4 == 0) {
	// subArt = "lifestyle;;;";
	// } else if (i % 3 == 0) {
	// subArt = "lifestyle;ORIG1;ORGB;";
	// } else if (i % 2 == 0) {
	// subArt = "lifestyle;abc;ORGA;";
	// } else {
	// // catch all
	// subArt = ";;;";
	// }
	//
	// pubSub.Join(m.getIp(), m.getPort());
	// pubSub.Subscribe(m.getIp(), m.getPort(), subArt);
	// }
	// }

	@Override
	public boolean PublishServer(String article, String ip, int port)
			throws RemoteException {
		return publishArticle(article, ip, port, false);
	}

	@Override
	public synchronized boolean Leave(String ip, int port)
			throws RemoteException {
		ClientMachine machine = new ClientMachine(ip, port);
		if (this.attachedClients.contains(machine)) {
			logger.warn(machine + " not joined");
			return false;
		}
		this.attachedClients.contains(machine);
		return true;
	}

	@Override
	public synchronized boolean LeaveServer(String ip, int port)
			throws RemoteException {
		ServerMachine machine = new ServerMachine(ip, port, null, null);
		logger.debug(machine + " trying to leave");
		if (this.attachedServers.contains(machine)) {
			logger.warn(machine + " not joined");
			return false;
		}
		this.attachedServers.contains(machine);
		logger.info(machine + " left successfully");
		return true;
	}

	private static boolean setSystemProperties() {
		File file = new File(".");
		String path = null;
		try {
			path = file.getCanonicalPath();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		if (path == null) {
			logger.error("FAILED");
			return false;
		}
		path = path.replace("\\", "/");
		if (!path.endsWith("/")) {
			path = path + "/";
		}
		logger.debug(path);
		System.setProperty("java.security.policy", "file:/" + path
				+ "server.policy");
		System.setProperty("java.rmi.server.codebase", "file:/" + path);
		return true;
	}

	private static void showUsage() {
		logger.info("Usage: PublisherServer start <registryHost> [<registryPort>]");
	}

	public static PublisherServer server;
	public static Communicate stub;

	private class GetListProcessor extends Thread {
		boolean shutDownInvoked = false;
		Object lockObj = null;
		long totalTimeToSleep = 0;

		public GetListProcessor(Object lock, long timeToSleep) {
			this.lockObj = lock;
			this.totalTimeToSleep = timeToSleep;
		}

		@Override
		public void run() {
			while (!shutDownInvoked) {
				try {
					synchronized (this.lockObj) {
						this.lockObj.wait(this.totalTimeToSleep);
					}
					PublisherServer.this.udpServerOperations.getList();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	private void shutdown() throws RemoteException, NotBoundException {
		logger.debug("shutting down server");
		try {
			this.getListProcessor.shutDownInvoked = true;
			this.udpServerOperations.deregisterServer();
			this.udpServerOperations.shutdown();
			this.getListProcessor.interrupt();
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		for (ServerMachine m : this.attachedServers) {
			try {
				Communicate stub = m.getServerStub();
				if (stub != null) {
					logger.info("leaving server " + m);
					// TODO: may be create a mymahine instance to keep it all
					stub.LeaveServer(Utils.getLocalServerIp(), rmiPort);
				}
			} catch (Exception e) {
				logger.error(e.getMessage(), e);
			}
		}
		this.unbindRmiRegistry();
		this.artDispatcher.shutDown();
		this.collectorMatcher.shutDown();
	}

	private void unbindRmiRegistry() {
		logger.debug("unbinding rmi registry");
		try {
			Registry registry = LocateRegistry.getRegistry(
					Utils.getLocalServerIp(), rmiPort);
			registry.unbind(SERVER_NAME);
			UnicastRemoteObject.unexportObject(server, false);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	private static void initCommands() {
		/**
		 * Commands 1) JoinServer 2) GetList 3) Stop
		 */
		Command.addCommand(JOINSERVER_COMAND, getJoinServerParameters());
		Command.addCommand(GETLIST_COMMAND, null);
		Command.addCommand(SHUTDOWN_COMMAND, null);
	}

	private static Parameter[] getJoinServerParameters() {
		Parameter joinServerIP = new Parameter("joinServerIP",
				PARA_TYPE.REQUIRED, null, false);
		Parameter joinServerPort = new Parameter("joinServerPort",
				PARA_TYPE.REQUIRED, null, true);
		return new Parameter[] { joinServerIP, joinServerPort };
	}

	public static void main(String[] args) throws RemoteException {
		if (args.length < 3) {
			showUsage();
			return;
		}
		int rmiPort = 1099;
		int registryPort;
		try {
			rmiPort = Integer.parseInt(args[0]);
			registryPort = Integer.parseInt(args[2]);
		} catch (NumberFormatException e) {
			System.out.print("Invalid port. ");
			showUsage();
			return;
		}
		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new SecurityManager());
		}
		// setSystemProperties();
		try {
			// int udpPort = Utils.findFreePort(2000);
			// int [] portList = new int [Props.THREADPOOL_SIZE];
			// for(int i=0; i<portList.length; i++) {
			// portList[i] = Utils.findFreePort(2000);
			// }
			Props.loadProperties();
			server = new PublisherServer(Props.PING_PORT, Props.FREE_PORT_LIST,
					rmiPort, args[1], registryPort);
			Registry registry = LocateRegistry.getRegistry(
					Utils.getLocalServerIp(), rmiPort);
			stub = (Communicate) UnicastRemoteObject.exportObject(
					(Communicate) server, 0);
			registry.bind(SERVER_NAME, stub);
		} catch (Exception e) {
			logger.error("Cannot start PublisherServer. Exception:", e);
			try {
				server.shutdown();
			} catch (NotBoundException e1) {
			}
			return;
		}
		if (!server.udpServerOperations.registerServer(Communicate.SERVER_NAME)) {
			server.unbindRmiRegistry();
			logger.error("cannot register to registry server");
			return;
		}
		/**
		 * Starting the console acceptance methods
		 */
		Console console = System.console();
		while (true) {
			String command = console.readLine(">>");
			ParsedCommand pc = null;
			try {
				pc = Command.parseCommand(command);
			} catch (CommandNotParsedException e) {
				System.out.println(e.getMessage());
			}
			if (pc != null) {
				if (pc.getComm().getCommandName().equals(GETLIST_COMMAND)) {
					synchronized (server.getListInvokerLock) {
						server.getListInvokerLock.notify();
					}
				} else if (pc.getComm().getCommandName()
						.equals(SHUTDOWN_COMMAND)) {
					try {
						server.shutdown();
					} catch (NotBoundException e) {
						logger.error("Error in pubsub", e);
					}
					break;
				} else {
					logger.error("problem with command configurations");
				}
			}
		}
	}
}
