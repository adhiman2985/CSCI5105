import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

import org.umn.distributed.common.Props;
import org.umn.distributed.common.UDPClient;
import org.umn.distributed.common.Utils;

public class ServerRegistryClient extends UDPClient {
	LinkedBlockingQueue<DatagramPacket> blockingQueue = new LinkedBlockingQueue<DatagramPacket>();
	private String registryIp = null;
	private int registryPort;
	private ServerMachine myServerMachine;
	private HashMap<ServerMachine, ServerMachine> knownServerMachines = null;
	private Set<ServerMachine> attachedServers = null;

	// TODO: May be handle the writerPort and readerPort logic here only.
	public ServerRegistryClient(String registryIp, int registryPort,
			int senderPort, int listenerPort,
			HashMap<ServerMachine, ServerMachine> knownServerMachines,
			Set<ServerMachine> attachedServers, int rmiPort)
			throws SocketException {
		super(senderPort, listenerPort);
		this.registryIp = registryIp;
		this.registryPort = registryPort;
		this.knownServerMachines = knownServerMachines;
		this.attachedServers = attachedServers;
		this.myServerMachine = new ServerMachine(Utils.getLocalServerIp(),
				rmiPort, null, null);
		;
		PacketProcessor processor = new PacketProcessor(blockingQueue);
		processor.start();
	}

	public boolean registerServer(String bindingName) {
		logger.debug("register the server to registry server");
		StringBuilder builder = new StringBuilder();
		builder.append("Register;RMI;");
		builder.append(myServerMachine.getRmiRegistryIp()).append(";");
		builder.append(getListenerPort()).append(";").append(bindingName);
		builder.append(";").append(myServerMachine.getPort());
		return sendData(builder.toString(), registryIp, registryPort);
	}

	public boolean deregisterServer() {
		logger.debug("deregister the server from registry server");
		StringBuilder builder = new StringBuilder();
		builder.append("Deregister;RMI;");
		builder.append(myServerMachine.getRmiRegistryIp()).append(";");
		builder.append(getListenerPort());
		return sendData(builder.toString(), registryIp, registryPort);
	}

	public boolean getList() {
		logger.debug("calling getList on registry server");
		StringBuilder builder = new StringBuilder();
		builder.append("GetList;RMI;");
		builder.append(myServerMachine.getRmiRegistryIp()).append(";");
		builder.append(getListenerPort());
		return sendDataAndReceive(builder.toString(), registryIp, registryPort);
	}

	@Override
	protected void handleRecieved(DatagramPacket packet, DatagramSocket socket)
			throws IOException {
		logger.debug("ping received from registry server");
		DatagramPacket resp = new DatagramPacket(packet.getData(),
				packet.getLength(), packet.getAddress(), packet.getPort());
		socket.send(resp);
	}

	@Override
	protected void processRecievedPacket(DatagramPacket packet) {
		logger.debug("packet received from registry server");
		blockingQueue.offer(packet);
	}

	private class PacketProcessor extends Thread {
		LinkedBlockingQueue<DatagramPacket> packetQueue = null;

		PacketProcessor(LinkedBlockingQueue<DatagramPacket> pPacketQueue) {
			this.packetQueue = pPacketQueue;
		}

		public void run() {
			while (!ServerRegistryClient.this.isShutdownInvoked()) {
				DatagramPacket packet = null;
				try {
					packet = this.packetQueue.take();
					processGetListResponse(packet);
				} catch (InterruptedException e) {
					logger.error(e.getMessage(), e);
				}
			}
			logger.info("Closed the datagram processor");
		}
	}

	private void processGetListResponse(DatagramPacket packet) {
		String servers = Utils.getDataFromPacket(packet, Props.ENCODING);
		logger.debug("parsing getList response: [" + servers + "]");
		String[] serversList = servers.split(";");
		if (serversList.length % 3 != 0) {
			logger.warn("invalid format in getList response: " + servers);
		}
		// TODO: update the list of the server and ask the server to join it.
		// now we need to add to our attachedServerList names for all the
		// servers who have previously joined us
		// and send the request to join to those who have not joined us yet.
		// TODO extend machine and add hostname to server machine
		// then update the attachedServers with hostnames
		// and finally call the remaining server on JoinServer
		this.knownServerMachines.clear();
		for (int i = 0; i < serversList.length - 2; i = i + 3) {
			try {
				ServerMachine sm = new ServerMachine(serversList[i],
						Integer.parseInt(serversList[i + 2]),
						serversList[i + 1], null);
				this.knownServerMachines.put(sm, sm);
			} catch (NumberFormatException e) {
				logger.error("invalid port: " + serversList[i + 2]
						+ " for server: " + serversList[i], e);
			}
		}
		Iterator<ServerMachine> it = this.attachedServers.iterator();
		Set<ServerMachine> toRemove = new HashSet<ServerMachine>();
		while (it.hasNext()) {
			ServerMachine sm = it.next();
			if (this.knownServerMachines.containsKey(sm)) {
				if (sm.getServerStub() == null) {
					try {
						logger.debug("creating stub for " + sm);
						ServerMachine m = this.knownServerMachines.get(sm);
						sm.setBindingName(m.getBindingName());
						Registry registry = LocateRegistry.getRegistry(
								sm.getRmiRegistryIp(), sm.getPort());
						Communicate stub;
						stub = (Communicate) registry.lookup(sm
								.getBindingName());
						sm.setServerStub(stub);
					} catch (RemoteException e) {
						logger.error(sm
								+ " threw RemoteException. Cannot join this server");
					} catch (NotBoundException e) {
						logger.error(sm + " not bound. Cannot join this server");
					}
				}
			} else {
				toRemove.add(sm);
			}
		}
		it = toRemove.iterator();
		while (it.hasNext()) {
			ServerMachine sm = it.next();
			logger.debug(sm + " not found in serverregistry. removing it");
			Communicate stub = sm.getServerStub();
			if (stub != null) {
				try {
					stub.LeaveServer(myServerMachine.getRmiRegistryIp(),
							myServerMachine.getPort());
				} catch (RemoteException e) {
					logger.error("error joining server " + sm, e);
				}
			}
			this.attachedServers.remove(sm);
		}
		for (ServerMachine sm : this.knownServerMachines.keySet()) {
			try {
				if (!this.attachedServers.contains(sm)) {
					logger.debug("trying to joinserver: " + sm);
					Registry registry = LocateRegistry.getRegistry(
							sm.getRmiRegistryIp(), sm.getPort());
					Communicate stub;
					stub = (Communicate) registry.lookup(sm.getBindingName());
					sm.setServerStub(stub);
					if (stub != null) {
						stub.JoinServer(myServerMachine.getRmiRegistryIp(),
								myServerMachine.getPort());
						this.attachedServers.add(sm);
						logger.info("joined the server: " + sm);
					}
				}
			} catch (RemoteException e) {
				logger.error(sm
						+ " threw RemoteException. Cannot join this server");
			} catch (NotBoundException e) {
				logger.error(sm + " not bound. Cannot join this server");
			}
		}
		logger.debug("attached server list: " + attachedServers);
	}
}
