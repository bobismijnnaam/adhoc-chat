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

public class ReliableSocket implements AdhocListener, Runnable {
	private static final byte ACK_TYPE = 2;

	private static final long RESEND_TIME = 1000;

	private AdhocSocket socket;

	private Random random = new Random();

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
		if (packet.getType() != ACK_TYPE) {
			if (packet.getDestAddress() != AdhocSocket.MULTICAST_ADDRESS) {
				try {
					ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
					DataOutputStream dataStream = new DataOutputStream(byteStream);

					dataStream.writeInt(packet.getId());

					socket.sendData(packet.getSourceAddress(), ACK_TYPE, byteStream.toByteArray());
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
				for (Packet p : unackedPackets) {
					if (p.getId() == id) {
						ackedPacket = p;
						break;
					}
				}

				unackedPackets.remove(ackedPacket);
				resendTimes.remove(ackedPacket);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void run() {
		while (socket.isRunning()) {
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

	public void send(byte dest, byte type, byte[] data) throws IOException {
		Packet packet = new Packet(socket.getAddress(), dest, (byte) 8, type, random.nextInt(), data);

		socket.sendData(packet);

		if (dest != AdhocSocket.MULTICAST_ADDRESS) {
			unackedPackets.add(packet);
			resendTimes.put(packet, System.currentTimeMillis() + RESEND_TIME);
		}
	}

	public void close() {
		socket.close();
	}

	@Override
	public void newConnection(Connection connection) {
		for (AdhocListener listener : listeners) {
			listener.newConnection(connection);
		}
	}

	@Override
	public void removedConnection(Connection connection) {
		ArrayList<Packet> removals = new ArrayList<Packet>();

		for (Packet p : unackedPackets) {
			if (p.getDestAddress() == connection.address) {
				removals.add(p);

				resendTimes.remove(p);
			}
		}

		unackedPackets.removeAll(removals);

		for (AdhocListener listener : listeners) {
			listener.removedConnection(connection);
		}
	}
}
