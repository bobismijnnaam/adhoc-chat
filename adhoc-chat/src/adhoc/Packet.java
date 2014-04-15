package adhoc;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

public class Packet {

	// packet types
	public static final byte TYPE_BROADCAST = 0;
	public static final byte TYPE_CHAT = 1;
	public static final byte TYPE_ACK = 2;
	public static final byte TYPE_LEAVE = 3;
	public static final byte TYPE_FILE = 4;
	public static final byte TYPE_FILE_OFFER = 5;
	public static final byte TYPE_FILE_ACCEPT = 6;
	public static final byte TYPE_FILE_ACK = 7;

	public static byte BROADCAST = 0;
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

	public DataInputStream getDataInputStream() {
		return new DataInputStream(new ByteArrayInputStream(data));
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

	@Override
	public int hashCode() {
		return getId();
	}

}
