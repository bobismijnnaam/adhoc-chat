package adhoc;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public class Download {

	// general
	private String filename;
	private int downloadNr;

	private long startTime;
	private long sizeBytes;
	private byte srcAddress; // destination if sending, source if downloading
	private byte dstAddress;
	private boolean hasStarted = false;

	// receiving
	private Map<Integer, byte[]> receivedPackets;

	public Download(String filename, int downloadNr, long sizeBytes, byte srcAddress, byte dstAddress) {
		this.filename = filename;
		this.downloadNr = downloadNr;
		this.sizeBytes = sizeBytes;
		this.srcAddress = srcAddress;
		this.dstAddress = dstAddress;
	}

	/**
	 * Call onStart() when it's ready to start receiving data, i.e. just sent
	 * back accept 'downloadNr'
	 */
	public void onStart() {
		receivedPackets = new HashMap<Integer, byte[]>();
		startTime = System.currentTimeMillis();
		hasStarted = true;
	}

	/**
	 * Process received data and return whether it has received all packets of
	 * the download.
	 * 
	 * @param receivedDownloadSeqNr
	 *            - seqNr of the data
	 * @param receivedBuffer
	 *            - filedata as bytes
	 * @return true if it's done
	 */
	public boolean receiveData(int receivedDownloadSeqNr, byte[] receivedBuffer) {
		if (receivedPackets.containsKey(receivedDownloadSeqNr)) {
			return false;
		}

		receivedPackets.put(receivedDownloadSeqNr, receivedBuffer);
		System.out.println(receivedPackets.size() + " of " + getNumberOfPackets()
				+ " packets received! Packet size was: " + receivedBuffer.length);

		if (getNumberOfPackets() == receivedPackets.size()) {
			try {
				System.out.println("DONE receivign!");
				// done downloading, now sort and write to disk
				Map<Integer, byte[]> sortedReceivedPackets = new TreeMap<Integer, byte[]>(receivedPackets);

				File file = new File(FileTransferSocket.FOLDER_RECEIVE + "/" + filename);
				FileOutputStream fos = new FileOutputStream(file, false);

				BufferedOutputStream bufStream = new BufferedOutputStream(fos);

				for (Iterator<byte[]> it = sortedReceivedPackets.values().iterator(); it.hasNext();) {
					byte[] bytes = (byte[]) it.next();
					bufStream.write(bytes);
				}
				bufStream.flush();
				bufStream.close();
				return true;
			} catch (IOException e) {
				System.out.println("ERROR writing file to disk!");
				e.printStackTrace();
				return true;
			}
		} else {
			return false;
		}
	}

	/**
	 * return how fast it's transferred
	 * 
	 * @return speed in Kbps
	 */
	public long getTransferSpeed() { // Kbps
		if (((System.currentTimeMillis() - startTime) / 1000) == 0) {
			return Integer.MAX_VALUE; // infinity!!!
		}
		return (sizeBytes * 8 / 1000) / ((System.currentTimeMillis() - startTime) / 1000);
	}

	public int getDownloadNr() {
		return downloadNr;
	}

	public byte getDstAddress() {
		return dstAddress;
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
}