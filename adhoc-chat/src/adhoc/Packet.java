package adhoc;

public class Packet {
	
	//packet types
	public static final int TYPE_BROADCAST 		= 0;
	public static final int TYPE_CHAT_PRIVATE	= 1;
	public static final int TYPE_LEAVE	 		= 2;
	public static final int TYPE_ACK 			= 3;
	public static final byte TYPE_FILE 			= 4;
	
	private byte sourceAddress, destAddress, hopCount, type;
	private byte[] data;
	private int id;
	
	public Packet(byte sourceAddress, byte destAddress, byte hopCount, byte type, int id, byte[] data) {
		this.sourceAddress = sourceAddress;
		this.destAddress = destAddress;
		this.hopCount = hopCount;
		this.data = data;
		this.type = type;
		this.id = id;
	}
	
	public byte getSourceAddress() {
		return sourceAddress;
	}
	
	public byte getDestAddress() {
		return destAddress;
	}
	
	public byte getHopCount() {
		return hopCount;
	}
	
	public byte getType() {
		return type;
	}
	
	public byte[] getData() {
		return data;
	}
	
	public int getId() {
		return id;
	}
}
