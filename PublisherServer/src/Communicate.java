import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Communicate extends Remote {
	public static final String SERVER_NAME = "PublisherServer";
	public static final int CLIENT_DEFAULT_UDP_PORT = 4455;
			
	/**
	 * TODO Question why do we not have the ip and the port address in the
	 * request, Do we get this information from the RMI call, should this be
	 * transparent Also if we do get this information then why do we need to
	 * send this information in subscribe and unsubscribe call.
	 * http://stackoverflow
	 * .com/questions/590257/determine-remote-client-ip-address
	 * -for-java-rmi-call
	 * 
	 * @param article
	 * @return
	 */
	public boolean Publish(String article, String ip, int port)
			throws RemoteException;

	/**
	 * If the client is present in the joined list then send back an
	 * acknowledgment TODO Prevent DOS attack ?
	 * 
	 * @return
	 */
	public boolean Ping() throws RemoteException;

	boolean PublishServer(String article, String ip, int port)
			throws RemoteException;

	boolean Leave(String ip, int port) throws RemoteException;

	boolean LeaveServer(String ip, int port) throws RemoteException;

	boolean JoinServer(String ip, int port) throws RemoteException;

	boolean Unsubscribe(String ip, int port, String article) throws RemoteException;

	boolean Subscribe(String ip, int port, String articleString) throws RemoteException;

	boolean Join(String ip, int port) throws RemoteException;
}
