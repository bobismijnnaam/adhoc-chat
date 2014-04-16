package adhoc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import adhoc.AdhocSocket.AdhocListener;

public class FileTransferSocket implements AdhocListener, Runnable {

	public static final String FOLDER_RECEIVE = "received";

	private static final long PACKET_TIMEOUT = 500;
	static final long OFFER_TIMEOUT = 10000;
	static final int PACKET_SIZE = 1024; // bytes
	private static final int PREF_WINDOWSIZE = 512; // set by receiver

	AdhocSocket socket;

	static Random random = new Random();
	int seqNr = random.nextInt();

	private HashMap<TCPPacket, Long> unackedPackets = new HashMap<TCPPacket, Long>();

	private int mode = 0; // 0 is receiving, 1 is sender

	private List<FileTransferListener> listeners = new ArrayList<FileTransferListener>();

	// downloads <downloadNr, dl>
	private HashMap<Integer, Download> downloads = new HashMap<Integer, Download>();
	private HashMap<Integer, Upload> uploads = new HashMap<Integer, Upload>();

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
		int downloadNr = random.nextInt();

		Upload u = new Upload(filepath, downloadNr, sizeBytes, socket.getAddress(), dstAddress);

		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
		DataOutputStream dataStream = new DataOutputStream(byteStream);

		dataStream.writeInt(seqNr);
		dataStream.writeInt(downloadNr);
		dataStream.writeUTF(filename);
		dataStream.writeLong(sizeBytes);

		Packet innerpacket = new Packet(socket.getAddress(), dstAddress, (byte) 8, Packet.TYPE_FILE_OFFER,
				random.nextInt(), byteStream.toByteArray());
		TCPPacket packet = new TCPPacket(innerpacket, seqNr++, downloadNr);

		// add packet to queue
		if (dstAddress != AdhocSocket.MULTICAST_ADDRESS) {
			synchronized (downloads) {
				uploads.put(u.getDownloadNr(), u);
			}
			synchronized (unackedPackets) {
				unackedPackets.put(packet, System.currentTimeMillis());
			}
		}
	}

	@Override
	public void run() {
		try {
			while (socket.isRunning()) {

				// remove timed-out offers
				synchronized (uploads) {
					for (Iterator<Upload> it = uploads.values().iterator(); it.hasNext();) {
						Upload upload = (Upload) it.next();
						if (!upload.hasStarted()) {
							// check for expired offers
							if (System.currentTimeMillis() >= upload.getTimeout()) {
								synchronized (unackedPackets) {
									unackedPackets.remove(upload);
								}
								it.remove();
								System.out.println("offer timed out; ");
							}
						} else {
							while (true) {
								TCPPacket next = upload.getNextPacket(seqNr);
								if (next == null) {
									break;
								} else {
									synchronized (unackedPackets) {
										seqNr++;
										unackedPackets.put(next, System.currentTimeMillis());
									}
								}
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

	@Override
	public void onReceive(Packet receivedPacket) {
		if (receivedPacket.getType() == 0) {
			return;
		}

		ByteArrayInputStream byteStreamIn = new ByteArrayInputStream(receivedPacket.getData());
		DataInputStream dataStreamIn = new DataInputStream(byteStreamIn);

		int receivedSeqNr = 0; // my seqNr
		int downloadNr = -1;
		try {
			receivedSeqNr = dataStreamIn.readInt();
			downloadNr = dataStreamIn.readInt();
		} catch (IOException e1) {
			System.out.println("FTS: couldn't read all contents.");
		}

		TCPPacket tcpPacket = new TCPPacket(receivedPacket, receivedSeqNr, downloadNr);

		// 'offer accepted!'
		if (downloadNr != -1 && tcpPacket.getType() == Packet.TYPE_FILE_ACCEPT) {
			synchronized (uploads) {

				Upload upload = uploads.get(tcpPacket.getOfferNr());
				try {
					int windowSize = dataStreamIn.readInt();
					if (upload != null) {
						upload.begin(windowSize);
					} else {
						System.out.println("Download offer not found/timed out");
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		// 'offer rejected :( '
		if (downloadNr != -1 && tcpPacket.getType() == Packet.TYPE_FILE_DECLINE) {
			synchronized (uploads) {

				Upload upload = uploads.get(tcpPacket.getOfferNr());
				if (upload != null) {
					for (FileTransferListener l : listeners) {
						l.onOfferRejected(upload);
					}
				} else {
					System.out.println("Download offer not found/timed out");
				}
			}
		}

		// receive file data
		if (downloadNr != -1 && tcpPacket.getType() == Packet.TYPE_FILE) {

			try {
				synchronized (downloads) {

					int receivedDownloadSeqNr = dataStreamIn.readInt();
					byte[] receivedBuffer = new byte[dataStreamIn.available()];
					dataStreamIn.read(receivedBuffer);

					Download download = downloads.get(downloadNr);
					if (download != null) {
						boolean finished = download.receiveData(receivedDownloadSeqNr, receivedBuffer);
						if (finished) {
							Download success = downloads.remove(download);
							System.out.println("COULD REMOVE DOWNLOAD ? " + success);
							System.out.println("Finished Downloading file @ " + download.getTransferSpeed() + "Kb/s");
							for (FileTransferListener l : listeners) {
								l.onFileTransferComplete(download);
							}
						}

						// return ack for filedata
						ByteArrayOutputStream byteStreamOut = new ByteArrayOutputStream();
						DataOutputStream dataStreamOut = new DataOutputStream(byteStreamOut);

						dataStreamOut.writeInt(receivedSeqNr);
						dataStreamOut.writeInt(downloadNr);
						dataStreamOut.writeInt(receivedDownloadSeqNr);

						Packet acket = new Packet(socket.getAddress(), tcpPacket.getSourceAddress(), (byte) 8,
								Packet.TYPE_FILE_ACK, random.nextInt(), byteStreamOut.toByteArray());
						socket.sendData(acket);
					}
				}
			} catch (IOException e) {
				System.out.println("error");
				e.printStackTrace();
			}

		}

		// receive an file ACK (REMOVE FROM unackedpackets and give to upload
		// object
		if (tcpPacket.getType() == Packet.TYPE_FILE_ACK) {
			synchronized (unackedPackets) {
				Long remove = unackedPackets.remove(tcpPacket);
				if (remove != null) {
					try {
						synchronized (uploads) {
							int recDownloadSeqAckNr = dataStreamIn.readInt();
							boolean finished = uploads.get(downloadNr).receiveAck(recDownloadSeqAckNr);
							if (finished) {
								System.out.println("Done uploading file!");
							}
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}

		// receive other acks
		if (tcpPacket.getType() == Packet.TYPE_ACK) {
			synchronized (unackedPackets) {
				unackedPackets.remove(tcpPacket);
			}
		}

		// return an ACK for non file packets
		if ((tcpPacket.getType() == Packet.TYPE_FILE_OFFER) || tcpPacket.getType() == Packet.TYPE_FILE_ACCEPT
				|| tcpPacket.getType() == Packet.TYPE_FILE_DECLINE) {
			try {
				ByteArrayOutputStream byteStreamOut = new ByteArrayOutputStream();
				DataOutputStream dataStreamOut = new DataOutputStream(byteStreamOut);

				dataStreamOut.writeInt(tcpPacket.getSeqNr());

				if (downloadNr != -1) {
					dataStreamOut.writeInt(downloadNr);
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

				Download d = new Download(filename, downloadNr, sizeBytes, tcpPacket.getSourceAddress(),
						socket.getAddress());
				downloads.put(d.getDownloadNr(), d);

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

				dataStreamOut.writeInt(seqNr);
				dataStreamOut.writeInt(d.getDownloadNr());
				int preferredWindowSize = PREF_WINDOWSIZE;
				dataStreamOut.writeInt(preferredWindowSize);

				synchronized (unackedPackets) {
					Packet acceptPacket = new Packet(socket.getAddress(), d.getAddress(), (byte) 8,
							Packet.TYPE_FILE_ACCEPT, random.nextInt(), byteStreamOut.toByteArray());
					TCPPacket tcpAcceptPacket = new TCPPacket(acceptPacket, seqNr++, d.getDownloadNr());
					unackedPackets.put(tcpAcceptPacket, System.currentTimeMillis());
				}

			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			try {
				System.out.println("REJECTED OFFER");
				// return 'decline' message to sender
				ByteArrayOutputStream byteStreamOut = new ByteArrayOutputStream();
				DataOutputStream dataStreamOut = new DataOutputStream(byteStreamOut);

				dataStreamOut.writeInt(seqNr);
				dataStreamOut.writeInt(d.getDownloadNr());

				synchronized (unackedPackets) {
					Packet acceptPacket = new Packet(socket.getAddress(), d.getAddress(), (byte) 8,
							Packet.TYPE_FILE_DECLINE, random.nextInt(), byteStreamOut.toByteArray());
					TCPPacket tcpAcceptPacket = new TCPPacket(acceptPacket, seqNr++, d.getDownloadNr());
					unackedPackets.put(tcpAcceptPacket, System.currentTimeMillis());
				}

				synchronized (downloads) {
					Download success = downloads.remove(d);
					System.out.println("Rejected, and could remove success? " + success);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
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

		public void onOfferRejected(Upload upload); // or timed-out
	}

	public void addListener(FileTransferListener l) {
		listeners.add(l);
	}

}
