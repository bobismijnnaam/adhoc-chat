package adhoc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import adhoc.AdhocSocket.AdhocListener;

/**
 * ReliableUDPSocket can reliably send packets to a destination, in this case using the AdhocSocket.
 * 
 * @author willem
 *
 */
public class ReliableUDPSocket implements Runnable, AdhocListener {

	/**
	 * unackedPackets - List of packets sent from 'this' client, not yet confirmed to be received
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
		} catch (IOException e) {
			e.printStackTrace();
		}
		new Thread(this).start();

	}

	/**
	 * Add to sending queue
	 * 
	 * @param dstAddress -
	 * @param data -- underlying data (e.g. textchat)
	 */
	public void sendReliable(byte dstAddress, byte[] data) {
		synchronized (unackedPackets) {
			UdpPacket toBeAcked = new UdpPacket(dstAddress, nextSeqNr++, data);
			unackedPackets.add(toBeAcked);
		}
	}

	public static void main(String[] args) {
		
		ReliableUDPSocket s = new ReliableUDPSocket();
		s.sendReliable((byte) 2, new byte[] { 1, 2, 52, 1, 2 });
		
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

							socket.sendData(packet.dstAddress,
									AdhocSocket.BROADCAST_TYPE,
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

			int seqNr = dataStream.readInt();
			// byte[] restData = new byte[dataStream.available()];
			// int offset = 0;
			// while(dataStream.available()>0){
			// dataStream.read(restData, offset, dataStream.available());
			// }

			// only destinationAddress and seqNr are needed for comparison
			UdpPacket received = new UdpPacket(packet.getDestAddress(), seqNr,
					null);

			synchronized (unackedPackets) {
				boolean success = unackedPackets.remove(received);
				System.out.println(" Success : " + success);
			}

		} catch (Exception e) {
			System.out.println(" NOOOOO !!! :( ");
			e.printStackTrace();
		}
		// dataInputStream.readByte(); //src
		// new Pair(dataInputStream.readByte(), )
		// unackedPackets.remove(index)
	}

	public class UdpPacket {

		private byte dstAddress;
		private int seqNr;
		private byte[] data;
		private int attemptCount = 0;

		// timing
		private long nextAttempt;
		private static final long RETRY_TIME = 1000; // 1 sec

		public UdpPacket(byte dstAddress, int seqNr, byte[] data) {
			this.dstAddress = dstAddress;
			this.seqNr = seqNr;
			this.data = data;
			this.nextAttempt = System.currentTimeMillis();
		}

		public byte[] compileData() {
			ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
			DataOutputStream dataStream = new DataOutputStream(byteStream);
			try {
				dataStream.write(seqNr);
				dataStream.write(data);
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

}
