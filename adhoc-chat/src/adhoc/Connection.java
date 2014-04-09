package adhoc;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

public class Connection {
	public byte address;
	public String name;
	public long lastBroadcast;
	
	public Connection(Packet packet, long lastBroadcast) throws IOException {
		ByteArrayInputStream byteStream = new ByteArrayInputStream(packet.getData());
		DataInputStream dataStream = new DataInputStream(byteStream);
		
		this.address = packet.getSourceAddress();
		this.name = dataStream.readUTF();
		this.lastBroadcast = lastBroadcast;
	}
}
