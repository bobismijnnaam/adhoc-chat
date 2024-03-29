package adhoc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import adhoc.AdhocSocket.AdhocListener;

/**
 * Provides a way to reliably send data to a host.
 */
public class ReliableSocket implements AdhocListener, Runnable {
	private static final long RESEND_TIME = 1000;

	private AdhocSocket socket;

	private Random random = new Random();

	/**
	 * Packets that are sent, but not yet acknowledged by their destination
	 * host.
	 */
	private ArrayList<Packet> unackedPackets = new ArrayList<Packet>();
	private HashMap<Packet, Long> resendTimes = new HashMap<Packet, Long>();

	private ArrayList<AdhocListener> listeners = new ArrayList<AdhocListener>();

	public static void main(String[] args) throws IOException {
		ReliableSocket reliableSocket = new ReliableSocket("alfred", new byte[0]);

		reliableSocket.send((byte) 2, (byte) 67, "haha test".getBytes());
	}

	public ReliableSocket(String name, byte[] key) throws IOException {
		socket = new AdhocSocket(name, key);
		socket.addListener(this);

		new Thread(this).start();
	}

	@Override
	public void onReceive(Packet packet) {
		if (packet.getType() != Packet.TYPE_ACK) {
			if (packet.getDestAddress() != AdhocSocket.MULTICAST_ADDRESS) {
				try {
					ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
					DataOutputStream dataStream = new DataOutputStream(byteStream);

					dataStream.writeInt(packet.getId());

					socket.sendData(packet.getSourceAddress(), Packet.TYPE_ACK, byteStream.toByteArray());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			for (AdhocListener listener : listeners) {
				listener.onReceive(packet);
			}
		} else {
			ByteArrayInputStream byteStream = new ByteArrayInputStream(packet.getData());
			DataInputStream dataStream = new DataInputStream(byteStream);

			try {
				int id = dataStream.readInt();

				Packet ackedPacket = null;
				synchronized (unackedPackets) {
					for (Packet p : unackedPackets) {
						if (p.getId() == id) {
							ackedPacket = p;
							break;
						}
					}

					unackedPackets.remove(ackedPacket);
					resendTimes.remove(ackedPacket);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void run() {
		while (socket.isRunning()) {
			synchronized (unackedPackets) {
				for (Packet p : unackedPackets) {
					if (System.currentTimeMillis() > resendTimes.get(p)) {
						try {
							socket.sendData(p);
						} catch (IOException e) {
							e.printStackTrace();
						}

						resendTimes.put(p, System.currentTimeMillis() + RESEND_TIME);
					}
				}
			}

			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	public void addListener(AdhocListener listener) {
		listeners.add(listener);
	}

	public void removeListener(AdhocListener listener) {
		listeners.remove(listener);
	}

	/**
	 * Sends the data in a reliable way, if the destination is not
	 * AdhocSocket.MULTICAST_ADDRESS.
	 */
	public void send(byte dest, byte type, byte[] data) throws IOException {
		Packet packet = new Packet(socket.getAddress(), dest, (byte) 8, type, random.nextInt(), data);

		socket.sendData(packet);

		if (dest != AdhocSocket.MULTICAST_ADDRESS) {
			synchronized (unackedPackets) {
				unackedPackets.add(packet);
				resendTimes.put(packet, System.currentTimeMillis() + RESEND_TIME);
			}
		}
	}

	public void close() {
		socket.close();
	}

	public AdhocSocket getAdhocSocket() {
		return socket;
	}

	@Override
	public void newConnection(Connection connection) {
		for (AdhocListener listener : listeners) {
			listener.newConnection(connection);
		}
	}

	@Override
	public void removedConnection(Connection connection) {
		synchronized (unackedPackets) {
			ArrayList<Packet> removals = new ArrayList<Packet>();

			for (Packet p : unackedPackets) {
				if (p.getDestAddress() == connection.address) {
					removals.add(p);

					resendTimes.remove(p);
				}
			}

			unackedPackets.removeAll(removals);
		}

		for (AdhocListener listener : listeners) {
			listener.removedConnection(connection);
		}
	}
}
