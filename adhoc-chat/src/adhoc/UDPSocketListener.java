package adhoc;

public interface UDPSocketListener {
	
	/**
	 * 
	 */
	public void onReceiveMessage(byte sourceAddress, long timestampMillis, String message);
	
	public void newConnection(Connection connection);
	
	public void removedConnection(Connection connection);
	
}
