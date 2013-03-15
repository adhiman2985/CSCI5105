import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.rmi.RemoteException;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.log4j.Logger;
import org.umn.distributed.server.ArticleWithDistributionList;
import org.umn.distributed.server.ClientMachine;
import org.umn.distributed.server.Machine;

/**
 * This would have one entry point used by the collectorAndMatcher which inserts
 * entries in the queue and then the workers can work on them
 * http://docs.oracle.com/javase/tutorial/networking/datagrams/clientServer.html
 * 
 * @author kinra003
 * 
 */
public class ArticleDispatcher {
	// TODO Evaluate ThreadPoolExecutor as it allows to specify timeouts, also
	// think about shuttingdown the server in an orderly fashion
	private ExecutorService distributionService;
	final private Set<ClientMachine> readOnlyAttachedClients;
	final private Set<ServerMachine> readOnlyAttachedServers;
	final private LinkedBlockingQueue<Integer> portNumbers = new LinkedBlockingQueue<Integer>();
	final private String myServerRMIIp;
	final private int myServerRMIPort;
	private Logger logger = Logger.getLogger(this.getClass());

	public void shutDown() {
		this.distributionService.shutdown();
	}

	public ArticleDispatcher(int maxThreads,
			Set<ClientMachine> readOnlyAttachedClients,
			Set<ServerMachine> readOnlyAttachedServers, String portList,
			String myServerRMIIp, int myServerRMIPort) {
		initPortList(portList);
		this.distributionService = Executors
				.newFixedThreadPool(this.portNumbers.size()); // at the time of
																// init it shows
																// the open
																// ports given
		this.readOnlyAttachedClients = readOnlyAttachedClients;
		this.readOnlyAttachedServers = readOnlyAttachedServers;
		this.myServerRMIIp = myServerRMIIp;
		this.myServerRMIPort = myServerRMIPort;
	}

	private void initPortList(String portList) {
		String[] ports = portList.split(",");
		for (String port1 : ports) {
			this.portNumbers.offer(Integer.parseInt(port1));
		}

	}

	protected boolean add(ArticleWithDistributionList adList) {
		this.distributionService.execute(new DispatchWorker(adList));
		return true;
	}

	private class DispatchWorker implements Runnable {
		private ArticleWithDistributionList artToSend;
		private Machine senderMachine;

		protected DispatchWorker(ArticleWithDistributionList a) {
			this.artToSend = a;
		}

		@Override
		public void run() {
			logger.debug("Processing article:" + this.artToSend);
			int availablePort = -1;
			if (this.artToSend.getDistributionCollection().size() > 0) {
				availablePort = ArticleDispatcher.this.portNumbers.poll();
			}
			try {
				for (Machine m : this.artToSend.getDistributionCollection()) {
					if (ArticleDispatcher.this.readOnlyAttachedClients
							.contains(m)
							|| ArticleDispatcher.this.readOnlyAttachedServers
									.contains(m)) {
						logger.info("Sending article ="
								+ this.artToSend.getArticle() + " to m=" + m);
						// if it is a clientMachine then we need to UDP
						// else we need to RMI
						if (m instanceof ClientMachine) {
							sendArticleThroughUDP(availablePort, m);
						} else {
							sendArticleThroughRMI(m);
						}

					}

				}
			} catch (Exception e) {
				logger.error("Error while running dispatch worker", e);

			} finally {
				ArticleDispatcher.this.portNumbers.offer(availablePort);
			}

		}

		private void sendArticleThroughRMI(Machine m) throws RemoteException {
			logger.debug("Sending article through RMI");
			ServerMachine sm = (ServerMachine) m;
			Communicate stub = sm.getServerStub();
			if(stub != null) {
				stub.PublishServer(this.artToSend.getArticle().getPublishFormat(),
						ArticleDispatcher.this.myServerRMIIp,
						ArticleDispatcher.this.myServerRMIPort);
			}

		}

		private void sendArticleThroughUDP(int availablePort, Machine m)
				throws IOException {
			ClientMachine cm = (ClientMachine) m;
			InetAddress address = null;
			logger.debug("Sending article through UDP");
			address = InetAddress.getByName(cm.getIp());
			DatagramPacket packet = new DatagramPacket(
					this.artToSend.getArticle().getArticleContent().getBytes(),
					this.artToSend.getArticle().getArticleContent().getBytes().length,
					address, m.getPort());
			DatagramSocket socket = new DatagramSocket(availablePort);
			socket.send(packet);
			socket.close();

		}
	}

}
