package org.umn.distributed.server;

public abstract class Machine {
	private int port;

	public int getPort() {
		return port;
	}

	public Machine(int machinePort) {
		this.port = machinePort;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + port;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Machine other = (Machine) obj;
		if (port != other.port)
			return false;
		return true;
	}
}
