package org.umn.distributed.client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

import org.umn.distributed.common.Props;
import org.umn.distributed.common.UDPClient;
import org.umn.distributed.common.Utils;

public class ClientUDPListener extends UDPClient {

	public ClientUDPListener(int senderPort) throws SocketException {
		super(senderPort);
	}

	public ClientUDPListener(int senderPort, int listenerPort)
			throws SocketException {
		super(senderPort, listenerPort);
	}

	@Override
	protected void handleRecieved(DatagramPacket packet, DatagramSocket socket)
			throws IOException {
		logger.debug("article recived from server");
		String packetData = null;
		packetData = Utils.getDataFromPacket(packet, Props.ENCODING);
		logger.info("article recieved: " + packetData);
	}

	@Override
	protected void processRecievedPacket(DatagramPacket packet) {

	}
}
