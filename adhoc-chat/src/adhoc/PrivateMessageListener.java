package adhoc;

public interface PrivateMessageListener {
	
	/**
	 * 
	 */
	public void onReceiveMessage(byte sourceAddress, long timestampMillis, String message);
	
}
