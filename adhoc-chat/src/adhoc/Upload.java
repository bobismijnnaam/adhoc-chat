package adhoc;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Upload {

	private static final long OFFER_TIMEOUT = 10000; // 10 sec

	// general
	private String filename;
	private int downloadNr;
	private long startTime;
	private long sizeBytes;
	private byte srcAddress;
	private byte dstAddress;
	private boolean hasStarted = false;

	// sending
	private long timeout;
	private byte[][] buffer;
	private int downloadSeqNr = 0;
	private int windowSize;
	private int nrOfAcksReceived;
	List<Integer> acksReceived = new ArrayList<Integer>();

	public Upload(String filename, int orderNr, long sizeBytes, byte srcAddress, byte dstAddress) {
		this.filename = filename;
		this.downloadNr = orderNr;
		this.sizeBytes = sizeBytes;
		this.srcAddress = srcAddress;
		this.dstAddress = dstAddress;

		timeout = (System.currentTimeMillis() + OFFER_TIMEOUT);
	}

	public boolean hasStarted() {
		return hasStarted;
	}

	public int getNumberOfPackets() {
		return (int) Math.ceil((float) sizeBytes / (float) FileTransferSocket.PACKET_SIZE);
	}

	public byte getAddress() {
		return srcAddress;
	}

	public String getFilename() {
		return filename;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public long getTransferSpeed() { // Kbps
		if (((System.currentTimeMillis() - startTime) / 1000) == 0) {
			return Integer.MAX_VALUE; // infinity!!!
		}
		return (sizeBytes * 8 / 1000) / ((System.currentTimeMillis() - startTime) / 1000);
	}

	/**
	 * Call when it's ready to start uploading, i.e. received 'offer accepted'.
	 * Reads the file to be sent into memory (byte[] toSend).
	 * 
	 * @param windowSize
	 *            - window size to send
	 */
	public void begin(int windowSize) {
		this.downloadSeqNr = 0;
		this.windowSize = windowSize;

		BufferedInputStream bufStream = null;
		try {
			File file = new File(filename);
			FileInputStream fis;
			fis = new FileInputStream(file);
			bufStream = new BufferedInputStream(fis);

			// Load file seqmented into memory
			buffer = new byte[getNumberOfPackets()][FileTransferSocket.PACKET_SIZE];

			int i = 0;
			while (bufStream.available() > 0) {
				byte[] toSend = new byte[Math.min(bufStream.available(), FileTransferSocket.PACKET_SIZE)];
				bufStream.read(toSend);
				buffer[i++] = toSend;
			}
			bufStream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		hasStarted = true;
		startTime = System.currentTimeMillis();
	}

	/**
	 * Get the next packet to be sent, if the window isn't full, and it isn't
	 * finished.
	 * 
	 * @return the next packet, or null if window is full or it's finished.
	 */
	public TCPPacket getNextPacket(int seqnrToUse) {
		if (downloadSeqNr < windowSize + nrOfAcksReceived && downloadSeqNr < getNumberOfPackets()) {

			ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
			try {
				DataOutputStream dataStream = new DataOutputStream(byteStream);
				dataStream.writeInt(seqnrToUse);
				dataStream.writeInt(downloadNr);
				dataStream.writeInt(downloadSeqNr);
				dataStream.write(buffer[downloadSeqNr++]);
			} catch (IOException e) {
				e.printStackTrace();
			}
			System.out.println("Getting next packet data size = " + buffer[downloadSeqNr - 1].length);
			return new TCPPacket(new Packet(this.srcAddress, this.dstAddress, (byte) 8, Packet.TYPE_FILE,
					FileTransferSocket.random.nextInt(), byteStream.toByteArray()), seqnrToUse, downloadNr);
		} else {
			return null;
		}
	}

	/**
	 * Process an ack, and return whether it has received all acks, and thus is
	 * done.
	 * 
	 * @param receivedDownloadSeqNrAck
	 *            - downloadSeqNr that was acked.
	 * @return whether it has received all acks, and thus is done.
	 */
	public boolean receiveAck(int receivedDownloadSeqNrAck) {
		if (!acksReceived.contains(receivedDownloadSeqNrAck)) {
			acksReceived.add(receivedDownloadSeqNrAck);
			nrOfAcksReceived++;
			System.out.println("Nr of acks received " + nrOfAcksReceived + " of " + getNumberOfPackets());
		}
		if (nrOfAcksReceived == getNumberOfPackets()) {
			return true;
		} else {
			return false;
		}
	}

	public long getTimeout() {
		return timeout;
	}

	public int getDownloadNr() {
		return downloadNr;
	}

}