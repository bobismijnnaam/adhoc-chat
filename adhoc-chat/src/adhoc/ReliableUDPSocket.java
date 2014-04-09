package adhoc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
	 * toBeAcked - List of acks needed to be sent back to message's the origin
	 */
	private List<UdpPacket> toBeAcked = new ArrayList<UdpPacket>();

	/**
	 * listeners - registered listeners for chat-messages
	 */
	private List<PrivateMessageListener> listeners = new ArrayList<PrivateMessageListener>();

	/**
	 * nextSeqNr - sequence number to use for the next packet to be sent
	 */
	private int nextSeqNr = 0;

	/**
	 * socket - lower layer (see {@link AdhocSocket})
	 */
	private AdhocSocket socket;

	public ReliableUDPSocket(String username) {
		try {
			socket = new AdhocSocket(username);
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
			UdpPacket toBeAcked = new UdpPacket(UdpPacket.TYPE_CHAT, dstAddress, nextSeqNr++, data);
			unackedPackets.add(toBeAcked);
		}
	}

	/**
	 * Send a chat
	 * 
	 * @param dstAddress
	 * @param timeStamp
	 * @param message
	 */
	public void sendChatMessage(byte dstAddress, long timeStamp, String message) {
		UdpPacket chatPacket = new UdpPacket(UdpPacket.TYPE_CHAT, dstAddress, nextSeqNr++, timeStamp, message);
		sendReliable(dstAddress, chatPacket.compileData());
	}

	// unit test
	public static void main(String[] args) {

		ReliableUDPSocket s = new ReliableUDPSocket("Test");

	}

	/**
	 * Add listener
	 * 
	 * @param l
	 */
	public void registerListener(PrivateMessageListener l) {
		listeners.add(l);
	}

	@Override
	public void run() {
		while (true) {
			long now = System.currentTimeMillis();
			synchronized (unackedPackets) {
				for (UdpPacket packet : unackedPackets) {
					if (packet.shouldSend(now)) {
						try {
							System.out.println("SEND PKT=" + packet.seqNr + " ATTEMPT=" + packet.attemptCount);
							socket.sendData(packet.dstAddress, (byte) 1, packet.compileData());
							packet.onSend();
						} catch (IOException e) {
							socket.close();
							e.printStackTrace();
						}
					}
				}
			}
			synchronized (toBeAcked) {
				for (Iterator<UdpPacket> iterator = toBeAcked.iterator(); iterator.hasNext();) {
					UdpPacket p = (UdpPacket) iterator.next();
					try {
						socket.sendData(p.dstAddress, (byte) 1, p.compileData());
						iterator.remove();
					} catch (IOException e) {
						e.printStackTrace();
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
			ByteArrayInputStream byteStream = new ByteArrayInputStream(packet.getData());
			DataInputStream dataStream = new DataInputStream(byteStream);

			byte packetType = dataStream.readByte();
			int seqNr = dataStream.readInt();

			// get 'chat header' if not ack
			if (packetType == UdpPacket.TYPE_CHAT) {

				long timestampMillis = dataStream.readLong();
				String message = dataStream.readUTF();

				for (PrivateMessageListener l : listeners) {
					l.onReceiveMessage(packet.getSourceAddress(), timestampMillis, message);
				}

				synchronized (toBeAcked) {
					UdpPacket acket = new UdpPacket(UdpPacket.TYPE_ACK, packet.getSourceAddress(), seqNr, new byte[] {});
					toBeAcked.add(acket);
				}
			}

			// only destinationAddress and seqNr are needed for comparison
			UdpPacket received = new UdpPacket(packetType, packet.getDestAddress(), seqNr, null);
			if (packetType == UdpPacket.TYPE_ACK) {
				synchronized (unackedPackets) {
					boolean success = unackedPackets.remove(received);
					System.out.println(" Packet Acked succes? : " + success);
				}
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

		// chat only fields
		private long timeStamp;
		private String message;

		// timing
		private long nextAttempt;
		private static final long RETRY_TIME = 2500; // 1 sec

		public UdpPacket(byte packetType, byte dstAddress, int seqNr, byte[] data) {
			this.packetType = packetType; // 1st byte
			this.seqNr = seqNr; // next 4 bytes
			this.dstAddress = dstAddress; // specified in super header
			this.data = data; // tail of packet, unused in TYPE_ACK
			this.nextAttempt = System.currentTimeMillis();
		}

		public UdpPacket(byte packetType, byte dstAddress, int seqNr, long timeStamp, String message) {
			this.packetType = packetType; // 1st byte
			this.seqNr = seqNr; // next 4 bytes
			this.dstAddress = dstAddress; // specified in super header
			this.nextAttempt = System.currentTimeMillis();

			this.timeStamp = timeStamp;
			this.message = message;
		}

		public byte[] compileData() {
			ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
			DataOutputStream dataStream = new DataOutputStream(byteStream);
			try {
				dataStream.write(packetType);
				dataStream.writeInt(seqNr);
				if (packetType == TYPE_CHAT) {
					dataStream.writeLong(timeStamp);
					dataStream.writeUTF(message);
				}
			} catch (IOException e) {
				System.out.println("ERROR COMPILING UDP PACKET!");
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
				System.out.println(other.seqNr + " ---- " + this.seqNr);
				if (other.seqNr == this.seqNr) {
					return true;
				}
				return (this.dstAddress == other.dstAddress) && (this.seqNr == other.seqNr);
			}
			return false;
		}

	}

	@Override
	public void newConnection(Connection connection) {
	}

	public void removedConnection(Connection connection) {
	};
}
