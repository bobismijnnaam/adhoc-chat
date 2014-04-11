package adhoc;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;

import adhoc.AdhocSocket.AdhocListener;

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
			FileTransferSocket sock = new FileTransferSocket(new AdhocSocket("willem" + random.nextInt(), new byte[] {
					0, 0, 0, 0 }));

			sock.makeOffer((byte) 1, "cheatcodes.txt");

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

				// send un-acked packets
				for (Iterator<TCPPacket> it = unackedPackets.keySet().iterator(); it.hasNext();) {
					TCPPacket packet = (TCPPacket) it.next();
					long timeToSend = unackedPackets.get(packet);
					if (System.currentTimeMillis() >= timeToSend) {
						System.out.println("sending a packet type=" + packet.getType());
						unackedPackets.put(packet, timeToSend + RESEND_TIME);
						socket.sendData(packet);
					}
				}

				if (state == 1 && mode == 1) {
					// fill up queue
					while (lastSeqSent < lastAckReceived + sendWindowSize) {
						System.out.println(" STATE is " + state);
						lastSeqSent++;
					}
				}

				Thread.sleep(10);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	int lastSeqSent = 0;
	int lastAckReceived = -1;
	int sendWindowSize = 5;

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
				}
				System.out.println("onReceive() remove(offer) size was: " + previousSize + " now size is :"
						+ pendingOffers.size());
				if (previousSize > pendingOffers.size()) {
					state = 1;
					try {
						beginSendingFile(dataStreamIn.readUTF(), dataStreamIn.readInt(),
								receivedPacket.getSourceAddress());
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}

		// return an ACK
		if ((tcpPacket.getType() == Packet.TYPE_FILE_OFFER && mode == 0)
				|| tcpPacket.getType() == Packet.TYPE_FILE_ACCEPT && mode == 1
				|| tcpPacket.getType() == Packet.TYPE_FILE) {
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
			for (Iterator<TCPPacket> it = unackedPackets.keySet().iterator(); it.hasNext();) {
				TCPPacket unacked = (TCPPacket) it.next();
				System.out.println("WAITING FOR AN ACK OF :" + unacked.getSeqNr());
			}
			Long remove = unackedPackets.remove(tcpPacket);
			System.out.println("receiving ack #" + receivedSeqNr + " Could remove it? = " + remove);
			if (remove != null) {
				lastAckReceived++;
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
					ByteArrayOutputStream byteStreamOut = new ByteArrayOutputStream();
					DataOutputStream dataStreamOut = new DataOutputStream(byteStreamOut);

					dataStreamOut.writeInt(seqNr++);
					dataStreamOut.writeInt(offerNr);
					dataStreamOut.writeUTF(dataStreamIn.readUTF()); // filename
					int preferredWindowSize = 128;
					dataStreamOut.writeInt(preferredWindowSize);

					System.out.println("Sending back ACCEPT for offer.");
					Packet acceptPacket = new Packet(socket.getAddress(), tcpPacket.getSourceAddress(), (byte) 8,
							Packet.TYPE_FILE_ACCEPT, random.nextInt(), byteStreamOut.toByteArray());
					TCPPacket tcpAcceptPacket = new TCPPacket(acceptPacket, seqNr, offerNr);
					unackedPackets.put(tcpAcceptPacket, System.currentTimeMillis());
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else {
				System.out.println("REJECTED PACKET");
			}
		}
		//
		// for (AdhocListener listener : listeners) {
		// listener.onReceive(packet);
		// }
		// ByteArrayInputStream byteStream = new
		// ByteArrayInputStream(packet.getData());
		// DataInputStream dataStream = new DataInputStream(byteStream);
		//
		// try {
		// int id = dataStream.readInt();
		//
		// Packet ackedPacket = null;
		// for (Packet p : unackedPackets) {
		// if (p.getId() == id) {
		// ackedPacket = p;
		// break;
		// }
		// }
		//
		// unackedPackets.remove(ackedPacket);
		// resendTimes.remove(ackedPacket);
		// } catch (IOException e) {
		// e.printStackTrace();
		// }

		// String filename = dataStream.readUTF();
		// int orderNr = dataStream.readInt();
		// String filecontents = dataStream.readUTF();
		//
		// File file = new File(RECEIVED_PREFIX + filename);
		// FileOutputStream fos;
		//
		// if (orderNr == 0) {
		// fos = new FileOutputStream(file, false);
		// } else {
		// fos = new FileOutputStream(file, true);
		// }
		// BufferedOutputStream bufStream = new BufferedOutputStream(fos);
		//
		// bufStream.write(filecontents.getBytes("UTF-8"));
		// System.out.println("Writing to file");
		// bufStream.flush();

		// } catch (EOFException e) {
		// System.out.println("File too large :(");
		// e.printStackTrace();
		// } catch (IOException e) {
		// System.out.println("Couldnt write file ");
		// e.printStackTrace();
		// }
	}

	private static final int PACKET_SIZE = 128; // bytes

	private void beginSendingFile(String filename, int windowSize, byte destination) {
		// filename in root (relative)
		BufferedInputStream bufStream = null;
		try {
			File file = new File(filename);
			FileInputStream fis;
			fis = new FileInputStream(file);
			bufStream = new BufferedInputStream(fis);

			long sizeBytes = file.length();

			byte[] toSend = new byte[PACKET_SIZE];
			TCPPacket[] packets = new TCPPacket[(int) Math.ceil((float) sizeBytes / (float) PACKET_SIZE)]; // round
																											// up???
			int i = 0;
			while (bufStream.available() > 0) {
				bufStream.read(toSend);
				// packets[i] = new TCPPacket(new Packet(socket.getAddress(),
				// destination, (byte)8, Packet.TYPE_FILE, random.nextInt(),
				// data),
				// destination, offerNr)*
			}

			ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
			DataOutputStream dataStream = new DataOutputStream(byteStream);
			dataStream.writeUTF(file.getName());
			dataStream.writeInt(++seqNr);
			dataStream.writeUTF(new String(toSend, "UTF-8"));

			// socket.send(destination, Packet.TYPE_FILE,
			// byteStream.toByteArray());
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			bufStream.close();
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
