import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.net.SocketException;
import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.log4j.Logger;
import org.umn.distributed.client.ClientUDPListener;
import org.umn.distributed.common.Command;
import org.umn.distributed.common.LoggingUtils;
import org.umn.distributed.common.Parameter;
import org.umn.distributed.common.Parameter.PARA_TYPE;
import org.umn.distributed.common.ParsedCommand;
import org.umn.distributed.common.Props;
import org.umn.distributed.common.Utils;

/**
 * <pre>
 * This class is client class. It starts a RMI clients and registers to
 * {@link PublisherServer}. It also starts a {@link ClientUDPListener}
 * which starts listening on port given through command line.
 * </pre>
 */
public class Client {
	private Logger logger = Logger.getLogger(this.getClass());
	private static final String COMMAND_JOIN = "joinserver";
	private static final String PARAM_JOIN_REGISTRYHOST = "registryHostname";
	private static final String PARAM_JOIN_REGISTRYPORT = "registryPort";
	private static final String PARAM_JOIN_LISTENERPORT = "listenerPort";
	private static final String COMMAND_LEAVE = "leaveserver";
	private static final String COMMAND_SUBSCRIBE = "subscribe";
	private static final String PARAM_SUBSCRIBE_CATEGORY = "category";
	private static final String COMMAND_UNSUBSCRIBE = "unsubscribe";
	private static final String COMMAND_PUBLISH = "publish";
	private static final String PARAM_PUBLISH_ARTICLE = "article";
	private static final String COMMAND_PING = "pingserver";
	private static final String COMMAND_STOP = "stopclient";

	//Initializes the commands for the command line tool
	static {
		// joinserver registryHostname registryPort
		Parameter[] p1 = new Parameter[3];
		p1[0] = new Parameter(PARAM_JOIN_REGISTRYHOST);
		p1[1] = new Parameter(PARAM_JOIN_LISTENERPORT, true);
		p1[2] = new Parameter(PARAM_JOIN_REGISTRYPORT, PARA_TYPE.OPTIONAL,
				"1099", true);
		Command.addCommand(COMMAND_JOIN, p1);

		//leaveserver
		Command.addCommand(COMMAND_LEAVE, null);

		//subscribe category
		Parameter[] p2 = new Parameter[1];
		p2[0] = new Parameter(PARAM_SUBSCRIBE_CATEGORY);
		Command.addCommand(COMMAND_SUBSCRIBE, p2);

		//unsubscribe category
		Parameter[] p3 = new Parameter[1];
		p3[0] = new Parameter(PARAM_SUBSCRIBE_CATEGORY);
		Command.addCommand(COMMAND_UNSUBSCRIBE, p3);

		// publish article
		Parameter[] p4 = new Parameter[1];
		p4[0] = new Parameter(PARAM_PUBLISH_ARTICLE);
		Command.addCommand(COMMAND_PUBLISH, p4);

		//pingserver
		Command.addCommand(COMMAND_PING, null);

		//stopclient
		Command.addCommand(COMMAND_STOP, null);
	}

	private ClientUDPListener listener = null;
	private Communicate stub = null;
	private String myIp = null;
	private int myPort;
	private List<String> subscribedArticle = new ArrayList<String>();

	public Client() {
		myIp = Utils.getLocalServerIp();
	}

	/**
	 * <pre>
	 * Sets the system properties for running the client.
	 * </pre>
	 * @return
	 * false if cannot find its location.
	 */
	private static boolean setSystemProperties() {
		File file = new File(".");
		String path = null;
		try {
			path = file.getCanonicalPath();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		if (path == null) {
			System.out.println("FAILED");
			return false;
		}
		path = path.replace("\\", "/");
		if (!path.endsWith("/")) {
			path = path + "/";
		}
		System.setProperty("java.security.policy", "file:/" + path
				+ "client.policy");
		System.setProperty("java.rmi.server.codebase", "file:/" + path + "bin/");
		return true;
	}

	public void setMyPort(int myPort) {
		this.myPort = myPort;
	}

	/**
	 * <pre>
	 * Pings the server to check if the server is bound and running or not.
	 * </pre>
	 * @return
	 * returns the response returned by server. false if server not reachable.
	 */
	private boolean isServerBoundAndRunning() {
		try {
			if(stub != null) {
				return stub.Ping();
			}
			else {
				logger.warn("server not connected");
			}
		} catch (RemoteException e) {
			logger.info("cannot ping server");
			logger.error(e.getMessage(), e);
		}
		return false;
	}

	
	private void join(String registryHostname, int registryPort) {
		
		try {
			Registry registry = LocateRegistry.getRegistry(registryHostname,
					registryPort);
			stub = (Communicate) registry.lookup(Communicate.SERVER_NAME);
			if (!stub.Ping()) {
				logger.error("Server not responding to ping while joining");
				return;
			}
			if (!stub.Join(myIp, myPort)) {
				logger.error("Client is not able to join the server");
				return;
			}
			//TODO: change the hardcoded senderPort
			listener = new ClientUDPListener(0, myPort);
			logger.info("Clients joined.");
		} catch (AccessException e) {
			logger.error("Join", e);
		} catch (RemoteException e) {
			logger.error("Join", e);
		} catch (NotBoundException e) {
			logger.error("Join", e);
		} catch(SocketException e) {
			logger.error("Join", e);
		}
	}

	private void leave() {
		if (isServerBoundAndRunning()) {
			try {
				if (!stub.Leave(myIp, myPort)) {
					logger.error("Leave command failed to unregister from Group server");
					return;
				}
				if(listener != null) {
					listener.shutdown();
				}
				stub = null;
			} catch (RemoteException e) {
				logger.error("Exception in Leave command from Group server", e);
			}
		}
	}

	private void subscribe(String category) {
		if (isServerBoundAndRunning()) {
			if (subscribedArticle.contains(category)) {
				logger.warn("No need to subscribe as already subscribed once");
				return;
			}
			try {
				if (!stub.Subscribe(myIp, myPort, category)) {
					logger.error("Cannot subscribe to category");
					return;
				}
				subscribedArticle.add(category);
			} catch (RemoteException e) {
				logger.error("Exception in subscribing", e);
			}
		}
	}

	private void unsubscribe(String category) {
		if (isServerBoundAndRunning()) {
			if (!subscribedArticle.contains(category)) {
				logger.error("Cannot unsubscribe a non subscribed category:"
						+ category + " \t subscribed categories ="
						+ subscribedArticle);
				// TODO: Do we unsubscribe sub category? In that case we have to
				// parse and check in the article list. sub - Cat1:All and
				// unsub-
				// cat:cat1??? Should it work?

				return;
			}
			try {
				if (!stub.Unsubscribe(myIp, myPort, category)) {
					logger.error("Cannot unsubscribe category:" + category
							+ " due to server issue");
					return;
				}
				subscribedArticle.remove(category);
			} catch (RemoteException e) {
				logger.error("Unsubscribe error", e);
			}
		}
	}

	public void publish(String article) {
		if (isServerBoundAndRunning()) {
			try {
				if (!stub.Publish(article, myIp, myPort)) {
					logger.error("Failed to publish article on server, article="
							+ article
							+ "server details (ip:port)=("
							+ this.stub);
					return;
				}
			} catch (RemoteException e) {
				logger.error("publish error", e);
			}
		}
	}

	public static void main(String args[]) {
		LoggingUtils.testLog("test");
		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new SecurityManager());
		}

		Client client = new Client();
		Props.loadProperties();
		boolean stopClient = false;
		// TODO maybe change this to Buffered input reader 
		Console console = System.console();
		while (!stopClient) {
			try {
				String commandRead = console.readLine(">>");
				ParsedCommand command = Command.parseCommand(commandRead);
				HashMap<String, String> params = command
						.getParsedParametersValue();
				if (command.getComm().getCommandName().equals(COMMAND_JOIN)) {
					int listenerPort = Integer.parseInt(params.get(PARAM_JOIN_LISTENERPORT));
					int port = Integer.parseInt(params
							.get(PARAM_JOIN_REGISTRYPORT));
					//TODO: create a validate port method
					if (port < 1 || port > 65535 || listenerPort < 1 || listenerPort > 65535) {
						System.out
								.println("Invalid port number. Enter in range 1-65535.");
					} else {
						client.setMyPort(listenerPort);
						client.join(params.get(PARAM_JOIN_REGISTRYHOST), port);
					}
				} else if (command.getComm().getCommandName()
						.equals(COMMAND_LEAVE)) {
					client.leave();
				} else if (command.getComm().getCommandName()
						.equals(COMMAND_SUBSCRIBE)) {
					client.subscribe(params.get(PARAM_SUBSCRIBE_CATEGORY));
				} else if (command.getComm().getCommandName()
						.equals(COMMAND_UNSUBSCRIBE)) {
					client.unsubscribe(params.get(PARAM_SUBSCRIBE_CATEGORY));
				} else if (command.getComm().getCommandName()
						.equals(COMMAND_PUBLISH)) {
					client.publish(params.get(PARAM_PUBLISH_ARTICLE));
				} else if (command.getComm().getCommandName()
						.equals(COMMAND_PING)) {
					client.isServerBoundAndRunning();
				} else if (command.getComm().getCommandName()
						.equals(COMMAND_STOP)) {
					client.leave();
					stopClient = true;
				}
			} catch (Exception e) {
				System.out.println(e.getMessage());
			}
		}
		System.out.println("after while");

	}
}