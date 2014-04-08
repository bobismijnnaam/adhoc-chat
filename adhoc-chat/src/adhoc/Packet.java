package adhoc;

public class Packet {
	
	private byte sourceAddress, destAddress, hopCount, type;
	private byte[] data;
	
	public Packet(byte sourceAddress, byte destAddress, byte hopCount, byte type, byte[] data) {
		this.sourceAddress = sourceAddress;
		this.destAddress = destAddress;
		this.hopCount = hopCount;
		this.data = data;
		this.type = type;
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
}
