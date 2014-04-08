package adhoc;

public class Connection {
	public byte address;
	public String name;
	public long lastBroadcast;
	
	public Connection(byte address, String name, long lastBroadcast) {
		this.address = address;
		this.name = name;
		this.lastBroadcast = lastBroadcast;
	}
}
