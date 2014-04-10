package adhoc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class UDPPacket {

	static final byte TYPE_CHAT = 0;
	static final byte TYPE_ACK = 1;

	private byte dstAddress;
	private byte packetType;
	private int seqNr;
	private int attemptCount = 0;

	// timing
	private long nextAttempt;
	private static final long RETRY_TIME = 2500; // 1 sec

	public UDPPacket(byte packetType, byte dstAddress, int seqNr) {
		this.packetType = packetType; // 1st byte (already set)f
		this.seqNr = seqNr; // next 4 bytes
		this.dstAddress = dstAddress; // specified in super header
		this.nextAttempt = System.currentTimeMillis();
	}

	public UDPPacket(ByteArrayInputStream byteStream) throws IOException {
		DataInputStream dataStream = new DataInputStream(byteStream);

		seqNr = dataStream.readInt();
		dstAddress = -123;
	}

	public byte[] compileData() {
		ByteArrayOutputStream byteStream = null;
		try {
			byteStream = getByteStream();
		} catch (IOException e) {
			System.out.println("ERROR COMPILING PACKET!");
			e.printStackTrace();
		}
		return byteStream.toByteArray();
	}

	protected ByteArrayOutputStream getByteStream() throws IOException {
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
		DataOutputStream dataStream = new DataOutputStream(byteStream);

		dataStream.write(getType());
		dataStream.writeInt(seqNr);

		// dataStream.flush();
		return byteStream;
	}

	public void onSend() {
		this.nextAttempt = System.currentTimeMillis() + RETRY_TIME;
		this.attemptCount++;
	}

	public boolean shouldSend(long now) {
		return (now >= nextAttempt);
	}

	@Override
	public String toString() {
		return Byte.toString(getDstAddress()) + Integer.toString(this.seqNr);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof UDPPacket) {
			UDPPacket other = (UDPPacket) obj;
			if (other.seqNr == this.seqNr) {
				return true;
			}
		}
		return false;
	}

	public byte getDstAddress() {
		return dstAddress;
	}

	public byte getType() {
		return packetType;
		// if (this instanceof ChatPacket) {
		// return TYPE_CHAT;
		// } else {
		// return TYPE_ACK;
		// }
	}

	public static UDPPacket parse(byte[] data) {
		ByteArrayInputStream byteStream = new ByteArrayInputStream(data);
		DataInputStream dataStream = new DataInputStream(byteStream);

		UDPPacket createdPacket = null;
		try {
			byte packetType = dataStream.readByte();
			// data[0] == TYPE_CHAT
			if (packetType == TYPE_CHAT) {
				createdPacket = new ChatPacket(byteStream);
			} else if (packetType == TYPE_ACK) {
				createdPacket = new UDPPacket(byteStream);
			}
		} catch (IOException e) {
			System.out.println("IOException occurred during parsing of packet");
			e.printStackTrace();
		}
		return createdPacket;
	}

	public void setType(byte type) {
		this.packetType = type;
	}

}