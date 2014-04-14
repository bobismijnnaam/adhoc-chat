package adhoc;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import adhoc.AdhocSocket.AdhocListener;
import crypto.Crypto;

public class FileTransferSocket implements AdhocListener, Runnable {

	private static final String RECEIVED_PREFIX = "received_";
	private static final long RESEND_TIME = 1000;
	private static final long OFFER_TIMEOUT = 5000;
	private AdhocSocket socket;

	private int state = 0;

	private static Random random = new Random();
	private int packetId = random.nextInt();
	private int seqNr = random.nextInt();

	private static HashMap<TCPPacket, Long> unackedPackets = new HashMap<TCPPacket, Long>();
	private HashMap<TCPPacket, Long> pendingOffers = new HashMap<TCPPacket, Long>();

	int mode = 0; // 0 is receiving, 1 is sender

	public static void main(String[] args) {
		try {
			FileTransferSocket sock = new FileTransferSocket(new AdhocSocket("willem" + random.nextInt(),
					Crypto.INSTANCE.getMyKey()));
			sock.makeOffer((byte) 1, "SPACE.jpg");
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
		// open file and construct packet
		File file = new File(filepath);
		long sizeBytes = file.length();
		String filename = file.getName();
		int offerNr = random.nextInt();
		int nrOfPackets = (int) Math.ceil((float) sizeBytes / (float) PACKET_SIZE);

		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
		DataOutputStream dataStream = new DataOutputStream(byteStream);

		dataStream.writeInt(seqNr);
		dataStream.writeInt(offerNr);
		dataStream.writeUTF(filename);
		dataStream.writeLong(sizeBytes);
		dataStream.writeInt(nrOfPackets);

		byte[] offerData = byteStream.toByteArray();
		Packet innerpacket = new Packet(socket.getAddress(), dstAddress, (byte) 8, Packet.TYPE_FILE_OFFER,
				random.nextInt(), offerData);
		TCPPacket packet = new TCPPacket(innerpacket, seqNr, offerNr);

		// add packet to queue
		if (dstAddress != AdhocSocket.MULTICAST_ADDRESS) {
			pendingOffers.put(packet, System.currentTimeMillis() + OFFER_TIMEOUT);
			unackedPackets.put(packet, System.currentTimeMillis());
		}

		seqNr++;
	}

	@Override
	public void run() {
		try {
			while (socket.isRunning()) {

				if (mode == 1) {
					// remove timed-out offers
					synchronized (pendingOffers) {
						for (Iterator<TCPPacket> it = pendingOffers.keySet().iterator(); it.hasNext();) {
							TCPPacket offer = (TCPPacket) it.next();
							if (System.currentTimeMillis() > pendingOffers.get(offer)) {
								it.remove();
								unackedPackets.remove(offer);
								System.out.print("offer timed out; ");
								System.out.println("# of pending offers now: " + pendingOffers.size());
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
							System.out.println("sending a packet type=" + packet.getType());
							unackedPackets.put(packet, timeToSend + RESEND_TIME);
							socket.sendData(packet);
						}
					}
				}

				// System.out.println("unacked packets.size() == " +
				// unackedPackets.size());

			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// received packets, not yet written
	private Map<Integer, byte[]> receivedPackets;
	// nr of packets received
	private int nrOfReceivedPackets = 0;
	// nr of packets to be received;
	private int currentDownloadNrOfPackets;

	// running order (receive side)
	private String currentDownloadName;
	private int lastSeqReceived;
	private long currentDownloadSize;

	@Override
	public void onReceive(Packet receivedPacket) {
		if (receivedPacket.getType() == 0) {
			return;
		}

		System.out.println("onReceive() type = " + receivedPacket.getType());

		// try {
		ByteArrayInputStream byteStreamIn = new ByteArrayInputStream(receivedPacket.getData());
		DataInputStream dataStreamIn = new DataInputStream(byteStreamIn);

		int receivedSeqNr = 0; // my seqNr
		int offerNr = -1;
		try {
			receivedSeqNr = dataStreamIn.readInt();
			if (receivedPacket.getType() != Packet.TYPE_ACK)
				offerNr = dataStreamIn.readInt();
		} catch (IOException e1) {
			System.out.println("Couldn't read !!! something in Onreceive()!!@");
			// e1.printStackTrace();
		}

		TCPPacket tcpPacket = new TCPPacket(receivedPacket, receivedSeqNr, offerNr);

		// 'offer accepted!'
		if (tcpPacket.getType() == Packet.TYPE_FILE_ACCEPT && mode == 1) {
			int previousSize = pendingOffers.size();
			synchronized (pendingOffers) {
				// System.out.println(packet.getId());
				for (Iterator<TCPPacket> it = pendingOffers.keySet().iterator(); it.hasNext();) {
					TCPPacket p = (TCPPacket) it.next();
					if (p.getOfferNr() == tcpPacket.getOfferNr()) {
						it.remove();
					}
					System.out.println("onReceive() remove(offer) size was: " + previousSize + " now size is :"
							+ pendingOffers.size());
				}
				if (previousSize > pendingOffers.size()) {
					state = 1;
					try {
						beginSendingFile(dataStreamIn.readUTF(), dataStreamIn.readInt(),
								receivedPacket.getSourceAddress(), tcpPacket.getOfferNr());
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}

		// receive file data
		if (tcpPacket.getType() == Packet.TYPE_FILE && mode == 0) {
			System.out.println("received filedata yay! (SEQNR = " + receivedSeqNr);

			try {
				if (receivedPackets.get(receivedSeqNr) == null) {
					// accept packet
					byte[] receivedBuffer = new byte[dataStreamIn.available()];
					dataStreamIn.read(receivedBuffer);

					receivedPackets.put(receivedSeqNr, receivedBuffer);
					nrOfReceivedPackets++;
				} else {
					System.out.println("Received Duplicate file data, ignoring.");
				}
				System.out.println(currentDownloadNrOfPackets + " ---- " + nrOfReceivedPackets);
				if (currentDownloadNrOfPackets == nrOfReceivedPackets) {
					System.out.println("Done downloading!");
					// done downloading, now sort and write to disk

					Map<Integer, byte[]> sortedReceivedPackets = new TreeMap<Integer, byte[]>(receivedPackets);

					File file = new File(RECEIVED_PREFIX + currentDownloadName);
					FileOutputStream fos = new FileOutputStream(file, false);

					BufferedOutputStream bufStream = new BufferedOutputStream(fos);

					for (Iterator<byte[]> it = sortedReceivedPackets.values().iterator(); it.hasNext();) {
						byte[] bytes = (byte[]) it.next();
						bufStream.write(bytes);
					}
					System.out.println("Writing to file");
					bufStream.flush();
				}

			} catch (IOException e) {
				System.out.println("error");
				e.printStackTrace();
			}

		}

		// write data to disk
		// String filename = currentDownloadName;

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

				System.out.println("sending back ACK for #" + tcpPacket.getSeqNr());
				Packet acket = new Packet(socket.getAddress(), tcpPacket.getSourceAddress(), (byte) 8, Packet.TYPE_ACK,
						random.nextInt(), byteStreamOut.toByteArray());
				socket.sendData(acket);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		// receive an ACK
		if (tcpPacket.getType() == Packet.TYPE_ACK) {
			synchronized (unackedPackets) {
				for (Iterator<TCPPacket> it = unackedPackets.keySet().iterator(); it.hasNext();) {
					TCPPacket unacked = (TCPPacket) it.next();
					System.out.println("WAITING FOR AN ACK OF :" + unacked.getSeqNr());
				}
				Long remove = unackedPackets.remove(tcpPacket);
				System.out.println("receiving ack #" + receivedSeqNr + " Could remove it? = " + remove);
			}
		}

		// receive an offer (return an 'accept')
		if (tcpPacket.getType() == Packet.TYPE_FILE_OFFER && mode == 0) {
			// prompt to accept or not...
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
			boolean accept = true;
			if (accept) {
				try {
					currentDownloadName = dataStreamIn.readUTF();
					currentDownloadSize = dataStreamIn.readLong();
					currentDownloadNrOfPackets = dataStreamIn.readInt();
					lastSeqReceived = receivedSeqNr;

					receivedPackets = new HashMap<Integer, byte[]>();

					ByteArrayOutputStream byteStreamOut = new ByteArrayOutputStream();
					DataOutputStream dataStreamOut = new DataOutputStream(byteStreamOut);

					dataStreamOut.writeInt(seqNr++);
					dataStreamOut.writeInt(offerNr);
					dataStreamOut.writeUTF(currentDownloadName); // filename
					int preferredWindowSize = 5;
					dataStreamOut.writeInt(preferredWindowSize);

					synchronized (unackedPackets) {
						System.out.println("Sending back ACCEPT for offer.");
						Packet acceptPacket = new Packet(socket.getAddress(), tcpPacket.getSourceAddress(), (byte) 8,
								Packet.TYPE_FILE_ACCEPT, random.nextInt(), byteStreamOut.toByteArray());
						TCPPacket tcpAcceptPacket = new TCPPacket(acceptPacket, seqNr, offerNr);
						unackedPackets.put(tcpAcceptPacket, System.currentTimeMillis());
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else {
				System.out.println("REJECTED PACKET");
			}
		}

	}

	private static final int PACKET_SIZE = 128; // bytes

	private void beginSendingFile(String filename, final int windowSize, byte destination, int offerNr) {
		// filename in root (relative)
		BufferedInputStream bufStream = null;
		try {
			File file = new File(filename);
			FileInputStream fis;
			fis = new FileInputStream(file);
			bufStream = new BufferedInputStream(fis);

			long sizeBytes = file.length();

			// Load file seqmented into memory
			final TCPPacket[] packets = new TCPPacket[(int) Math.ceil((float) sizeBytes / (float) PACKET_SIZE)]; // round
			int i = 0;
			while (bufStream.available() > 0) {
				byte[] toSend = new byte[Math.min(bufStream.available(), PACKET_SIZE)];
				bufStream.read(toSend);

				ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
				DataOutputStream dataStream = new DataOutputStream(byteStream);
				dataStream.writeInt(++seqNr);
				dataStream.writeInt(offerNr);
				dataStream.write(toSend);

				packets[i++] = new TCPPacket(new Packet(socket.getAddress(), destination, (byte) 8, Packet.TYPE_FILE,
						random.nextInt(), byteStream.toByteArray()), seqNr, offerNr);
			}
			bufStream.close();
			System.out.println("Read " + filename + " into memory success? "
					+ (packets.length == Math.ceil((float) sizeBytes / (float) PACKET_SIZE)));

			// queueing packets
			new Thread(new Runnable() {
				int nextPacket = 0;// index

				@Override
				public void run() {
					while (nextPacket < packets.length) {
						synchronized (unackedPackets) {

							if (unackedPackets.size() < windowSize) {
								System.out.println("QUEUEING PACKET INDEX = " + nextPacket + " SEQNR = "
										+ packets[nextPacket].getSeqNr());

								unackedPackets.put(packets[nextPacket++], System.currentTimeMillis());
							}
						}
					}
				}
			}).start();

		} catch (FileNotFoundException e) {
			System.out.println("File " + filename + " not found in project root");
		} catch (IOException c) {
			c.printStackTrace();
		}

	}

	@Override
	public void newConnection(Connection connection) {

	}

	@Override
	public void removedConnection(Connection connection) {

	}

}
