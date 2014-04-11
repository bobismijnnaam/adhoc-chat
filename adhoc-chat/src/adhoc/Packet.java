package adhoc;

public class Packet {

	public static byte BROADCAST = 0;
	public static byte CHAT = 1;
	public static byte ACK = 2;
	public static byte LEAVE = 3;

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
