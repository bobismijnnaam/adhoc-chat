package adhoc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import adhoc.AdhocSocket.AdhocListener;

/**
 * ReliableUDPSocket can reliably send packets to a destination, in this case
 * using the AdhocSocket.
 * 
 * @author willem
 * 
 */
public class ReliableUDPSocket implements Runnable, AdhocListener {

	/**
	 * unackedPackets - List of packets sent from 'this' client, not yet
	 * confirmed to be received
	 */
	private List<UdpPacket> unackedPackets = new ArrayList<UdpPacket>();

	/**
	 * nextSeqNr - sequence number to use for the next packet to be sent
	 */
	private int nextSeqNr = 0;

	/**
	 * socket - lower layer (see {@link AdhocSocket})
	 */
	private AdhocSocket socket;

	public ReliableUDPSocket() {
		try {
			socket = new AdhocSocket("willem " + new Random().nextInt());
			socket.addListener(this);
		} catch (IOException e) {
			e.printStackTrace();
		}
		new Thread(this).start();

	}

	/**
	 * Add to sending queue
	 * 
	 * @param dstAddress
	 *            -
	 * @param data
	 *            -- underlying data (e.g. textchat)
	 */
	public void sendReliable(byte dstAddress, byte[] data) {
		synchronized (unackedPackets) {
			UdpPacket toBeAcked = new UdpPacket(UdpPacket.TYPE_CHAT,
					dstAddress, nextSeqNr++, data);
			unackedPackets.add(toBeAcked);
		}
	}

	public static void main(String[] args) {

		ReliableUDPSocket s = new ReliableUDPSocket();
		s.sendReliable((byte) 3, "groeten".getBytes());

	}

	@Override
	public void run() {
		while (true) {
			long now = System.currentTimeMillis();
			synchronized (unackedPackets) {
				for (UdpPacket packet : unackedPackets) {
					if (packet.shouldSend(now)) {
						try {
							 System.out.println("SEND PKT=" + packet.seqNr
							 + " ATTEMPT=" + packet.attemptCount);
							socket.sendData(packet.dstAddress, (byte) 1,
									packet.compileData());
							packet.onSend();
						} catch (IOException e) {
							socket.close();
							e.printStackTrace();
						}
					}
				}
			}
		}
	}

	/**
	 * method of the AdhocListener
	 */
	@Override
	public void onReceive(Packet packet) {

		// get
		try {
			ByteArrayInputStream byteStream = new ByteArrayInputStream(
					packet.getData());
			DataInputStream dataStream = new DataInputStream(byteStream);

			byte packetType = dataStream.readByte();
			int seqNr = dataStream.readInt();

			// get 'chat header' if not ack
			byte[] restData = new byte[dataStream.available()];
			if (packetType != UdpPacket.TYPE_ACK) {
				int offset = 0;
				while (dataStream.available() > 0) {
					dataStream.read(restData, offset, dataStream.available());
				}
				System.out.println("Received data: " + Arrays.toString(restData));
			}

			// only destinationAddress and seqNr are needed for comparison
			UdpPacket received = new UdpPacket(packetType,
					packet.getDestAddress(), seqNr, restData);

			synchronized (unackedPackets) {
				boolean success = unackedPackets.remove(received);
				System.out.println(" Packet Acked succes? : " + success);
			}

		} catch (Exception e) {
			System.out.println(" NOOOOO !!! :( ");
			e.printStackTrace();
		}
	}
	
	public class UdpPacket {

		private static final byte TYPE_CHAT = 0;
		private static final byte TYPE_ACK = 1;

		private byte packetType = -1;
		private byte dstAddress;
		private int seqNr;
		private byte[] data;
		private int attemptCount = 0;

		// timing
		private long nextAttempt;
		private static final long RETRY_TIME = 1000; // 1 sec

		public UdpPacket(byte packetType, byte dstAddress, int seqNr,
				byte[] data) {
			this.packetType = packetType; // 1st byte
			this.seqNr = seqNr; // next 4 bytes
			this.dstAddress = dstAddress; // specified in super header
			this.data = data; // tail of packet, unused in TYPE_ACK
			this.nextAttempt = System.currentTimeMillis();
		}

		public byte[] compileData() {
			ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
			DataOutputStream dataStream = new DataOutputStream(byteStream);
			try {
				dataStream.write(packetType);
				dataStream.write(seqNr);
				if (packetType != TYPE_ACK) {
					dataStream.write(data);
				}
			} catch (IOException e) {
				System.out.println(" ERROR COMPILING UDP PACKET! ");
				e.printStackTrace();
			}
			return byteStream.toByteArray();
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
			return Byte.toString(dstAddress) + Integer.toString(this.seqNr);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof UdpPacket) {
				UdpPacket other = (UdpPacket) obj;
				return (this.dstAddress == other.dstAddress)
						&& (this.seqNr == other.seqNr);
			}
			return false;
		}

	}

	@Override
	public void newConnection(Connection connection) {
	}

}
