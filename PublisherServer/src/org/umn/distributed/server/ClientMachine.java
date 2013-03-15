package org.umn.distributed.server;

public class ClientMachine extends Machine{
	private String ip;

	public ClientMachine(String machineIp, int machinePort) {
		super(machinePort);
		this.ip = machineIp;
	}

	public String getIp() {
		return ip;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("[ip:").append(getIp());
		builder.append(",port:").append(getPort());
		builder.append("]");
		return builder.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((ip == null) ? 0 : ip.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		ClientMachine other = (ClientMachine) obj;
		if (ip == null) {
			if (other.ip != null)
				return false;
		} else if (!ip.equals(other.ip))
			return false;
		return true;
	}
}
