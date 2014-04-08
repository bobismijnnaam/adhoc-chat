package adhoc;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import adhoc.AdhocSocket.AdhocListener;

public class ReliableUDPSocket implements Runnable, AdhocListener {

	private AdhocSocket socket;

	public ReliableUDPSocket() {
		try {
			socket = new AdhocSocket();
		} catch (IOException e) {
			e.printStackTrace();
		}
		new Thread(this).start();

	}

	private List<UdpPacket> unackedPackets = new ArrayList<UdpPacket>();
	private int nextSeqNr = 0;

	/**
	 * Add to sending queue
	 * 
	 * @param dstAddress
	 * @param data -- underlying data
	 */
	public void sendReliable(byte dstAddress, byte[] data) {
		UdpPacket toBeAcked = new UdpPacket(dstAddress, nextSeqNr++, data);
		unackedPackets.add(toBeAcked);
	}

	public static void main(String[] args) {
		ReliableUDPSocket s = new ReliableUDPSocket();
		s.sendReliable((byte) 2, new byte[] { 1, 2, 52, 1, 2 });
	}

	@Override
	public void run() {
		while (true) {
			long now = System.currentTimeMillis();
			for (UdpPacket pair : unackedPackets) {
				if (pair.shouldSend(now)) {
					 try {
					System.out.println(" SEND "+pair.attemptCount);
					 socket.sendData(pair.dstAddress, pair.data);
					 pair.onSend();
					 } catch (IOException e) {
					 socket.close();
					 e.printStackTrace();
					 }
				}
			}
		}
	}
	
	@Override
	public void onReceive(DataInputStream dataInputStream) {
		synchronized(unackedPackets){
//			dataInputStream.readByte(); //src
//			new Pair(dataInputStream.readByte(), )
//			unackedPackets.remove(index)
		}
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
