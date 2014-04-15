package adhoc;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import adhoc.AdhocSocket.AdhocListener;
import crypto.Crypto;

public class FileTransferSocket implements AdhocListener, Runnable {

	public static final String FOLDER_RECEIVE = "received";

	private static final long PACKET_TIMEOUT = 500;
	private static final long OFFER_TIMEOUT = 10000;
	private static final int PACKET_SIZE = 1024; // bytes
	private static final int PREF_WINDOWSIZE = 1024; // set by receiver

	private AdhocSocket socket;

	private static Random random = new Random();
	private int seqNr = random.nextInt();

	private HashMap<TCPPacket, Long> unackedPackets = new HashMap<TCPPacket, Long>();

	private int mode = 0; // 0 is receiving, 1 is sender

	private List<FileTransferListener> listeners = new ArrayList<FileTransferListener>();

	public static void main(String[] args) {
		try {
			FileTransferSocket sock = new FileTransferSocket(new AdhocSocket("willem" + random.nextInt(),
					Crypto.INSTANCE.getMyKey()));
			// sock.makeOffer((byte) 1, "SPACE.jpg");
			//
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public FileTransferSocket(AdhocSocket socket) {
		this.socket = socket;
		socket.addListener(this);

		new Thread(this).start();
	}

	public void makeOffer(byte dstAddress, String filepath) throws IOException {
		System.out.println("making offer");
		mode = 1; // sender
		// open file
		File file = new File(filepath);
		long sizeBytes = file.length();
		String filename = file.getName();
		int offerNr = random.nextInt();

		Download d = new Download(file.getAbsolutePath(), offerNr, sizeBytes, dstAddress);
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
		DataOutputStream dataStream = new DataOutputStream(byteStream);

		dataStream.writeInt(seqNr);
		dataStream.writeInt(offerNr);
		dataStream.writeUTF(filename);
		dataStream.writeLong(sizeBytes);

		byte[] offerData = byteStream.toByteArray();
		Packet innerpacket = new Packet(socket.getAddress(), dstAddress, (byte) 8, Packet.TYPE_FILE_OFFER,
				random.nextInt(), offerData);
		TCPPacket packet = new TCPPacket(innerpacket, seqNr, offerNr);

		// add packet to queue
		if (dstAddress != AdhocSocket.MULTICAST_ADDRESS) {
			synchronized (downloads) {
				downloads.put(d.offerNr, d);
			}
			synchronized (unackedPackets) {
				unackedPackets.put(packet, System.currentTimeMillis());
			}
		}

		seqNr++;
	}

	@Override
	public void run() {
		try {
			while (socket.isRunning()) {

				if (mode == 1) {
					// remove timed-out offers
					synchronized (downloads) {
						for (Iterator<Download> it = downloads.values().iterator(); it.hasNext();) {
							Download dl = (Download) it.next();
							if (!dl.hasStarted()) {
								if (System.currentTimeMillis() >= dl.getTimeOut()) {
									synchronized (unackedPackets) {
										unackedPackets.remove(dl);
									}
									it.remove();
									System.out.print("offer timed out; ");
									// System.out.println("# of pending offers now: "
									// + pendingOffers.size());
								}
							} else {
								TCPPacket next = dl.getNextPacket();
								synchronized (unackedPackets) {
									while (next != null) {
										unackedPackets.put(next, System.currentTimeMillis());
										next = dl.getNextPacket();
									}
								}
								// now window is full

							}
						}
					}
				}

				// send un-acked packets
				synchronized (unackedPackets) {
					for (Iterator<TCPPacket> it = unackedPackets.keySet().iterator(); it.hasNext();) {
						TCPPacket packet = (TCPPacket) it.next();
						long timeToSend = unackedPackets.get(packet);
						if (System.currentTimeMillis() >= timeToSend) {
							// System.out.println("sending packet type=" +
							// packet.getType());
							unackedPackets.put(packet, timeToSend + PACKET_TIMEOUT);
							socket.sendData(packet);
						}
					}
				}

			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// receiver downloads <ordernr, dl>
	private HashMap<Integer, Download> downloads = new HashMap<Integer, Download>();

	public class Download {

		// general
		private String filename;
		private int offerNr;
		private long startTime;
		private long sizeBytes;
		private byte address; // destination if sending, source if downloading
		private boolean hasStarted = false;

		// receiving
		private Map<Integer, byte[]> receivedPackets;

		// sending
		private long timeout;
		private byte[][] buffer;
		private int downloadSeqNr = 0;
		private int windowSize;
		private int nrOfAcksReceived;

		public Download(String filename, int orderNr, long sizeBytes, byte otherAddress) {
			this.filename = filename;
			this.offerNr = orderNr;
			this.sizeBytes = sizeBytes;
			this.address = otherAddress;

			timeout = System.currentTimeMillis() + OFFER_TIMEOUT;
		}

		public boolean hasStarted() {
			return hasStarted;
		}

		public int getNumberOfPackets() {
			return (int) Math.ceil((float) sizeBytes / (float) PACKET_SIZE);
		}

		public byte getAddress() {
			return address;
		}

		public String getFilename() {
			return filename;
		}

		public long getSizeBytes() {
			return sizeBytes;
		}

		public long getTransferSpeed() { // Kbps
			return (sizeBytes * 8 / 1000) / ((System.currentTimeMillis() - startTime) / 1000);
		}

		// receive specific below here
		public void onStart() {
			receivedPackets = new HashMap<Integer, byte[]>();
			startTime = System.currentTimeMillis();
			hasStarted = true;
		}

		public long getTimeOut() {
			return timeout;
		}

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

					File file = new File(FOLDER_RECEIVE + "/" + filename);
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

		// sending specific below here
		private void begin(int windowSize) {
			this.downloadSeqNr = 0;
			this.windowSize = windowSize;
			// this.acksReceived = 0;
			BufferedInputStream bufStream = null;
			try {
				File file = new File(filename);
				FileInputStream fis;
				fis = new FileInputStream(file);
				bufStream = new BufferedInputStream(fis);

				// Load file seqmented into memory
				buffer = new byte[getNumberOfPackets()][PACKET_SIZE];

				int i = 0;
				while (bufStream.available() > 0) {
					byte[] toSend = new byte[Math.min(bufStream.available(), PACKET_SIZE)];
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

		private TCPPacket getNextPacket() {
			if (downloadSeqNr < windowSize + nrOfAcksReceived && downloadSeqNr < getNumberOfPackets()) {

				ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
				try {
					DataOutputStream dataStream = new DataOutputStream(byteStream);
					dataStream.writeInt(++seqNr);
					dataStream.writeInt(offerNr);
					dataStream.writeInt(downloadSeqNr);
					dataStream.write(buffer[downloadSeqNr++]);
				} catch (IOException e) {
					e.printStackTrace();
				}
				System.out.println("Getting next packet data size = " + buffer[downloadSeqNr - 1].length);
				return new TCPPacket(new Packet(socket.getAddress(), address, (byte) 8, Packet.TYPE_FILE,
						random.nextInt(), byteStream.toByteArray()), seqNr, offerNr);
			} else {
				return null;
			}
		}

		List<Integer> acksReceived = new ArrayList<Integer>();

		// return if it's done downloading
		private boolean receiveAck(int recDownloadSeqAckNr) {
			if (!acksReceived.contains(recDownloadSeqAckNr)) {
				acksReceived.add(recDownloadSeqAckNr);
				nrOfAcksReceived++;
				System.out.println("Nr of acks received " + nrOfAcksReceived + " of " + getNumberOfPackets());
			}
			if (nrOfAcksReceived == getNumberOfPackets()) {
				return true;
			} else {
				return false;
			}
		}
	}

	@Override
	public void onReceive(Packet receivedPacket) {
		if (receivedPacket.getType() == 0) {
			return;
		}

		ByteArrayInputStream byteStreamIn = new ByteArrayInputStream(receivedPacket.getData());
		DataInputStream dataStreamIn = new DataInputStream(byteStreamIn);

		int receivedSeqNr = 0; // my seqNr
		int offerNr = -1;
		try {
			receivedSeqNr = dataStreamIn.readInt();
			offerNr = dataStreamIn.readInt();
		} catch (IOException e1) {
			System.out.println("FTS: couldn't read all contents.");
		}

		TCPPacket tcpPacket = new TCPPacket(receivedPacket, receivedSeqNr, offerNr);

		// 'offer accepted!'
		if (tcpPacket.getType() == Packet.TYPE_FILE_ACCEPT && mode == 1) {
			synchronized (downloads) {

				Download download = downloads.get(tcpPacket.getOfferNr());
				try {
					int windowSize = dataStreamIn.readInt();
					if (download != null) {
						download.begin(windowSize);
					} else {
						System.out.println("Download offer not found/timed out");
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		// receive file data
		if (tcpPacket.getType() == Packet.TYPE_FILE && mode == 0) {

			try {
				int receivedDownloadSeqNr = dataStreamIn.readInt();
				byte[] receivedBuffer = new byte[dataStreamIn.available()];
				dataStreamIn.read(receivedBuffer);

				Download download = downloads.get(offerNr);
				boolean finished = download.receiveData(receivedDownloadSeqNr, receivedBuffer);
				if (finished) {
					System.out.println("Finished Downloading file");
					for (FileTransferListener l : listeners) {
						l.onFileTransferComplete(download);
					}
				}

				// return ack for filedata
				ByteArrayOutputStream byteStreamOut = new ByteArrayOutputStream();
				DataOutputStream dataStreamOut = new DataOutputStream(byteStreamOut);

				dataStreamOut.writeInt(receivedSeqNr);
				dataStreamOut.writeInt(offerNr);
				dataStreamOut.writeInt(receivedDownloadSeqNr);

				// System.out.println("sending back ACK for #" +
				// tcpPacket.getSeqNr());
				Packet acket = new Packet(socket.getAddress(), tcpPacket.getSourceAddress(), (byte) 8,
						Packet.TYPE_FILE_ACK, random.nextInt(), byteStreamOut.toByteArray());
				socket.sendData(acket);

			} catch (IOException e) {
				System.out.println("error");
				e.printStackTrace();
			}

		}

		// receive an file ACK
		if (tcpPacket.getType() == Packet.TYPE_FILE_ACK && mode == 1) {
			synchronized (unackedPackets) {
				Long remove = unackedPackets.remove(tcpPacket);
				if (remove != null) {
					try {
						int recDownloadSeqAckNr = dataStreamIn.readInt();
						boolean finished = downloads.get(offerNr).receiveAck(recDownloadSeqAckNr);
						if (finished) {
							System.out.println("Done uploading file!");
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				// System.out.println("receiving ack #" + receivedSeqNr +
				// " Could remove it? = " + remove);
			}
		}

		// receive other acks
		if (tcpPacket.getType() == Packet.TYPE_ACK) {
			synchronized (unackedPackets) {
				unackedPackets.remove(tcpPacket);
			}
		}

		// return an ACK
		if ((tcpPacket.getType() == Packet.TYPE_FILE_OFFER && mode == 0)
				|| tcpPacket.getType() == Packet.TYPE_FILE_ACCEPT && mode == 1
				|| tcpPacket.getType() == Packet.TYPE_FILE && mode == 0) {
			try {
				ByteArrayOutputStream byteStreamOut = new ByteArrayOutputStream();
				DataOutputStream dataStreamOut = new DataOutputStream(byteStreamOut);

				if (tcpPacket.getType() == Packet.TYPE_FILE_ACCEPT && mode == 1) {
					dataStreamOut.writeInt(tcpPacket.getSeqNr() + 1);
				} else {
					dataStreamOut.writeInt(tcpPacket.getSeqNr());
				}

				if (offerNr != -1) {
					dataStreamOut.writeInt(offerNr);
				}

				// System.out.println("sending back ACK for #" +
				// tcpPacket.getSeqNr());
				Packet acket = new Packet(socket.getAddress(), tcpPacket.getSourceAddress(), (byte) 8, Packet.TYPE_ACK,
						random.nextInt(), byteStreamOut.toByteArray());
				socket.sendData(acket);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		// receive an offer (return an 'accept')
		if (tcpPacket.getType() == Packet.TYPE_FILE_OFFER && mode == 0) {

			try {
				String filename = dataStreamIn.readUTF();
				long sizeBytes = dataStreamIn.readLong();

				Download d = new Download(filename, offerNr, sizeBytes, tcpPacket.getSourceAddress());
				// listeners....
				downloads.put(d.offerNr, d);

				for (FileTransferListener l : listeners) {
					l.onReceiveDownloadOffer(d);
				}
				// respondOffer(d, true);// remove!!! todo
			} catch (IOException e) {
				e.printStackTrace();
			}

		}
	}

	public void respondOffer(Download d, boolean accept) {
		if (accept) {
			try {
				File dir = new File(FOLDER_RECEIVE);
				if (!dir.exists()) {
					dir.mkdir();
				}

				d.onStart();

				// return 'accept' message to sender
				ByteArrayOutputStream byteStreamOut = new ByteArrayOutputStream();
				DataOutputStream dataStreamOut = new DataOutputStream(byteStreamOut);

				dataStreamOut.writeInt(seqNr++);
				dataStreamOut.writeInt(d.offerNr);
				int preferredWindowSize = PREF_WINDOWSIZE;
				dataStreamOut.writeInt(preferredWindowSize);

				synchronized (unackedPackets) {
					Packet acceptPacket = new Packet(socket.getAddress(), d.getAddress(), (byte) 8,
							Packet.TYPE_FILE_ACCEPT, random.nextInt(), byteStreamOut.toByteArray());
					TCPPacket tcpAcceptPacket = new TCPPacket(acceptPacket, seqNr, d.offerNr);
					unackedPackets.put(tcpAcceptPacket, System.currentTimeMillis());
				}

			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			System.out.println("REJECTED OFFER");
		}
	}

	@Override
	public void newConnection(Connection connection) {
	}

	@Override
	public void removedConnection(Connection connection) {
	}

	public interface FileTransferListener {
		public void onReceiveDownloadOffer(Download downloadOffer);

		public void onFileTransferComplete(Download download);

		public void onOfferRejected(Download download); // or timed-out
	}

	public void addListener(FileTransferListener l) {
		listeners.add(l);
	}

}
