package adhoc;

public class TCPPacket extends Packet {

	private int seqNr;
	private int offerNr;

	public static final int NONE = -12323;

	public TCPPacket(Packet packet, int seqNr, int offerNr) {
		super(packet.getSourceAddress(), packet.getDestAddress(), packet.getHopCount(), packet.getType(), packet
				.getId(), packet.getData());
		this.seqNr = seqNr;
		this.offerNr = offerNr;
	}

	public int getSeqNr() {
		return seqNr;
	}

	public int getOfferNr() {
		return offerNr;
	}

	@Override
	public boolean equals(Object obj) {
		TCPPacket other = (TCPPacket) obj;
		return getSeqNr() == other.getSeqNr();
	}

	@Override
	public int hashCode() {
		return getSeqNr();
	}

}