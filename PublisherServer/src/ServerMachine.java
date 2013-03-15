import org.umn.distributed.server.Machine;

public class ServerMachine extends Machine {
	private String rmiRegistryIp = null;
	private String bindingName = null;
	private Communicate serverStub = null;

	public ServerMachine(String rmiRegistryIp, int rmiPort, String bindingName,
			Communicate serverStub) {
		super(rmiPort);
		this.rmiRegistryIp = rmiRegistryIp;
		this.bindingName = bindingName;
		this.serverStub = serverStub;
	}

	public String getRmiRegistryIp() {
		return rmiRegistryIp;
	}

	public String getBindingName() {
		return bindingName;
	}

	public void setBindingName(String bindingName) {
		this.bindingName = bindingName;
	}

	public Communicate getServerStub() {
		return serverStub;
	}

	public void setServerStub(Communicate serverStub) {
		this.serverStub = serverStub;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("[rmiIp:").append(getRmiRegistryIp());
		builder.append(",rmiPort:").append(getPort());
		builder.append(",bindingName:").append(getBindingName());
		builder.append("]");
		return builder.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result
				+ ((rmiRegistryIp == null) ? 0 : rmiRegistryIp.hashCode());
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
		ServerMachine other = (ServerMachine) obj;
		if (rmiRegistryIp == null) {
			if (other.rmiRegistryIp != null)
				return false;
		} else if (!rmiRegistryIp.equals(other.rmiRegistryIp))
			return false;
		return true;
	}
}
