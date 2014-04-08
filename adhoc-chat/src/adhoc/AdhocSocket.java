package adhoc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;

public class AdhocSocket implements Runnable {
	private static final String ADDRESS = "226.1.2.3";
	private static final int PORT = 4001;
	protected static final long BROADCAST_TIME = 1000;

	public static final byte BROADCAST_TYPE = 0;

	public static final byte MULTICAST_ADDRESS = -1;

	protected static final long TIMEOUT = 10000;

	private MulticastSocket socket;

	private ArrayList<AdhocListener> listeners = new ArrayList<AdhocListener>();
	private ArrayList<Connection> connections = new ArrayList<Connection>();
	private boolean running = true;
	private byte address;
	private final String name;
	private InetAddress inetAddress;

	public static void main(String[] args) throws IOException {
		new AdhocSocket("test");
	}

	public AdhocSocket(final String name) throws IOException {
		this.name = name;

		socket = new MulticastSocket(PORT);

		address = getLocalAddress();

		System.out.println("Local address: " + address);

		inetAddress = InetAddress.getByName(ADDRESS);
		socket.joinGroup(inetAddress);

		new Thread(this).start();

		new Thread(new Runnable() {
			@Override
			public void run() {
				while (running) {
					try {
						Thread.sleep(BROADCAST_TIME);
					} catch (InterruptedException e) {
					}

					// send broadcast

					ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
					DataOutputStream dataStream = new DataOutputStream(
							byteStream);

					try {
						dataStream.writeUTF(name);

						sendData(MULTICAST_ADDRESS, BROADCAST_TYPE,
								byteStream.toByteArray());
					} catch (IOException e) {
						e.printStackTrace();
					}

					// check removed connections

					ArrayList<Connection> removals = new ArrayList<Connection>();

					for (Connection connection : connections) {
						if (System.currentTimeMillis()
								- connection.lastBroadcast > TIMEOUT) {
							System.out.println("removed connection "
									+ connection.address);
							removals.add(connection);
						}
					}

					connections.removeAll(removals);
				}
			}
		}).start();
	}

	private byte getLocalAddress() throws SocketException {
		Enumeration<InetAddress> addresses = NetworkInterface
				.getByName("wlan0").getInetAddresses();
		while (addresses.hasMoreElements()) {
			InetAddress element = addresses.nextElement();

			if (element instanceof Inet4Address)
				return ((Inet4Address) element).getAddress()[3];
		}

		System.out.println("Unable to find local address!");
		return -1;
	}

	public void run() {
		while (running) {
			try {
				byte[] buffer = new byte[1500];
				DatagramPacket p = new DatagramPacket(buffer, buffer.length);
				socket.receive(p);

				byte[] data = new byte[p.getLength()];
				System.arraycopy(buffer, 0, data, 0, p.getLength());

				onReceive(data);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void onReceive(byte[] buffer) throws IOException {
		System.out.println("received " + buffer.length);

		ByteArrayInputStream byteStream = new ByteArrayInputStream(buffer);
		DataInputStream dataStream = new DataInputStream(byteStream);

		byte source = dataStream.readByte();
		byte dest = dataStream.readByte();
		byte hopCount = dataStream.readByte();
		byte type = dataStream.readByte();

		byte[] data = new byte[byteStream.available()];
		dataStream.read(data);

		Packet packet = new Packet(source, dest, hopCount, type, data);

		System.out.println("Destination = " + dest + " Mine = " + address);
		System.out.println(dest != address);
		if (dest != address) {
			if (source != address) {
				if (hopCount > 0) {
					hopCount--;
					sendData(source, dest, hopCount, type, data);

					if (type == BROADCAST_TYPE) {
						handleBroadcast(packet);
					}
				}
			}
		} else {
			System.out.println(hopCount);
			if (hopCount == 0) {
				for (AdhocListener listener : listeners) {
					listener.onReceive(packet);
				}
			}
		}
	}

	private void handleBroadcast(Packet packet) {
		Connection connection = getConnection(packet.getSourceAddress());

		if (connection == null) {
			connection = new Connection(packet.getSourceAddress(), new String(
					packet.getData()), System.currentTimeMillis());
			connections.add(connection);

			for (AdhocListener listener : listeners) {
				listener.newConnection(connection);
			}
		} else {
			connection.lastBroadcast = System.currentTimeMillis();
		}
	}

	public Connection getConnection(byte address) {
		for (Connection connection : connections) {
			if (connection.address == address)
				return connection;
		}

		return null;
	}

	public void sendData(byte destAddress, byte packetType, byte[] data)
			throws IOException {
		sendData(address, destAddress, (byte) 8, packetType, data);
	}

	public void sendData(byte source, byte destAddress, byte hopCount,
			byte packetType, byte[] data) throws IOException {
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
		DataOutputStream dataStream = new DataOutputStream(byteStream);

		dataStream.write(source);
		dataStream.write(destAddress);
		dataStream.write(hopCount);
		dataStream.write(packetType);
		dataStream.write(data);

		socket.send(new DatagramPacket(byteStream.toByteArray(), byteStream
				.size(), inetAddress, PORT));
	}

	public void addListener(AdhocListener listener) {
		listeners.add(listener);
	}

	public void removeListener(AdhocListener listener) {
		listeners.remove(listener);
	}

	public void close() {
		running = false;

		// TODO: send leave message?

		socket.close();
	}

	public interface AdhocListener {
		public void onReceive(Packet packet);

		public void newConnection(Connection connection);
	}
}
