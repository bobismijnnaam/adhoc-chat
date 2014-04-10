package adhoc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class ChatPacket extends UDPPacket {

	private String message;
	private long timestamp;

	public ChatPacket(long timestamp, String message, byte dstAddress, int seqNr) {
		super(UDPPacket.TYPE_CHAT, dstAddress, seqNr);
		this.message = message;
		this.timestamp = timestamp;
		this.setType(TYPE_CHAT);
	}

	public ChatPacket(ByteArrayInputStream byteStream) throws IOException {
		super(byteStream);
		DataInputStream dataStream = new DataInputStream(byteStream);
		this.timestamp = dataStream.readLong();
		this.message = dataStream.readUTF();
	}

	@Override
	protected ByteArrayOutputStream getByteStream() throws IOException {
		ByteArrayOutputStream byteStream = super.getByteStream();
		DataOutputStream dataStream = new DataOutputStream(byteStream);

		dataStream.writeLong(timestamp);
		dataStream.writeUTF(message);

		return byteStream;
	}

	public String getMessage() {
		return message;
	}

	public long getTimeStamp() {
		return timestamp;
	}
	
	public byte getType(){
		return (byte) 0;
	}

}
