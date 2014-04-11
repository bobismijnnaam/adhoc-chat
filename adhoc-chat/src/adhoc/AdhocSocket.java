package adhoc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Random;

public class AdhocSocket implements Runnable {
	private static final String ADDRESS = "226.1.2.3";
	private static final int PORT = 4001;
	protected static final long BROADCAST_TIME = 1000;

	public static final byte MULTICAST_ADDRESS = -1;

	protected static final long TIMEOUT = 10000;

	private MulticastSocket socket;

	private ArrayList<AdhocListener> listeners = new ArrayList<AdhocListener>();
	private ArrayList<Connection> connections = new ArrayList<Connection>();
	private boolean running = true;
	private byte address;
	private InetAddress inetAddress;

	// used for filtering duplicate packets
	private int[] forwardedPackets = new int[1000];
	private int forwardedPacketsIndex = 0;

	private final Random random = new Random();

	public static void main(String[] args) throws IOException, InterruptedException {
		// try {
		// Thread.sleep(5000);
		// } catch (InterruptedException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }
		//
		// adhocSocket.close();

		AdhocSocket adhocSocket = new AdhocSocket(args[0], new byte[0]);

		AdhocListener ahListener = new AdhocListener() {
			@Override
			public void onReceive(Packet packet) {
				if (packet.getType() == Packet.BROADCAST) {
					System.out.println("[UNIT TEST] Received broadcast");
				} else {
					System.out.println("[UNIT TEST] Received message: " + new String(packet.getData()));
				}
			}

			@Override
			public void newConnection(Connection connection) {
				System.out.println("[UNIT TEST] New connection: " + connection.name);
			}

			@Override
			public void removedConnection(Connection connection) {
				System.out.println("[UNIT TEST] Removed connection: " + connection.name);
			}

		};

		adhocSocket.addListener(ahListener);

		byte client1 = (byte) 1;
		byte client2 = (byte) 2;

		if (args[0].equals("client1")) {

			Thread.sleep(2000);

			adhocSocket.sendData(client1, (byte) 1, "TESTMESSAGE1".getBytes());

			Thread.sleep(2000);

			adhocSocket.sendData(client1, (byte) 1, "TESTMESSAGE3".getBytes());
		} else {
			Thread.sleep(3000);

			adhocSocket.sendData(client2, (byte) 1, "TESTMESSAGE2".getBytes());

			Thread.sleep(2000);

			adhocSocket.sendData(client2, (byte) 1, "TESTMESSAGE4".getBytes());
		}

		adhocSocket.close();
	}

	public AdhocSocket(final String name, final byte[] key) throws IOException {
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
					// send broadcast

					ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
					DataOutputStream dataStream = new DataOutputStream(byteStream);

					try {
						dataStream.writeUTF(name);
						dataStream.write(key);

						sendData(MULTICAST_ADDRESS, Packet.BROADCAST, byteStream.toByteArray());
					} catch (IOException e) {
						e.printStackTrace();
					}

					// check removed connections

					ArrayList<Connection> removals = new ArrayList<Connection>();

					for (Connection connection : connections) {
						if (System.currentTimeMillis() - connection.lastBroadcast > TIMEOUT) {
							removals.add(connection);

							for (AdhocListener listener : listeners) {
								listener.removedConnection(connection);
							}
						}
					}

					connections.removeAll(removals);

					try {
						Thread.sleep(BROADCAST_TIME);
					} catch (InterruptedException e) {
					}
				}
			}
		}).start();
	}

	private byte getLocalAddress() throws SocketException {
		Enumeration<NetworkInterface> addresses = NetworkInterface.getNetworkInterfaces();
		while (addresses.hasMoreElements()) {
			NetworkInterface networkInteface = addresses.nextElement();

			Enumeration<InetAddress> inetAddresses = networkInteface.getInetAddresses();

			while (inetAddresses.hasMoreElements()) {
				InetAddress address = inetAddresses.nextElement();

				System.out.println(address);

				byte[] bytes = address.getAddress();

				if (bytes[0] == (byte) 192 && bytes[1] == (byte) 168 && bytes[2] == (byte) 5) {
					return address.getAddress()[3];
				}
			}
		}

		System.out.println("Unable to find local address!");
		return -1;
	}

	public void run() {
		while (running) {
			try {
				byte[] buffer = new byte[1500];// MTU is 1500 bytes
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
		ByteArrayInputStream byteStream = new ByteArrayInputStream(buffer);
		DataInputStream dataStream = new DataInputStream(byteStream);

		byte source = dataStream.readByte();
		byte dest = dataStream.readByte();
		byte hopCount = dataStream.readByte();
		byte type = dataStream.readByte();
		int id = dataStream.readInt();
		byte[] data = new byte[byteStream.available()];
		dataStream.read(data);

		Packet packet = new Packet(source, dest, hopCount, type, id, data);

		if (!isDuplicate(id))
			System.out.println("received " + Integer.toHexString(id) + " from " + source);

		if (dest == address || dest == MULTICAST_ADDRESS) {
			if (!isDuplicate(id)) {
				for (AdhocListener listener : listeners) {
					listener.onReceive(packet);
				}
			}
		}

		if (dest != address) {
			if (source != address) {
				if (hopCount > 0 && !isDuplicate(id)) {
					hopCount--;
					sendData(source, dest, hopCount, type, id, data);

					if (type == Packet.BROADCAST) {
						handleBroadcast(packet);
					}

					if (type == Packet.LEAVE) {
						Connection connection = getConnection(source);
						connections.remove(connection);

						for (AdhocListener listener : listeners) {
							listener.removedConnection(connection);
						}
					}
				}
			}
		}
	}

	private boolean isDuplicate(int packetId) {
		for (int i = 0; i < forwardedPackets.length; i++) {
			if (forwardedPackets[i] == packetId)
				return true;
		}

		return false;
	}

	private void handleBroadcast(Packet packet) throws IOException {
		Connection connection = getConnection(packet.getSourceAddress());

		if (connection == null) {
			connection = new Connection(packet, System.currentTimeMillis());
			connections.add(connection);

			System.out.println(connection.name + " has connected");

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

	public void sendData(byte destAddress, byte packetType, byte[] data) throws IOException {
		sendData(address, destAddress, (byte) 8, packetType, random.nextInt(), data);
	}

	public void sendData(Packet packet) throws IOException {
		sendData(packet.getSourceAddress(), packet.getDestAddress(), packet.getHopCount(), packet.getType(),
				packet.getId(), packet.getData());
	}

	private void sendData(byte source, byte destAddress, byte hopCount, byte packetType, int id, byte[] data)
			throws IOException {
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
		DataOutputStream dataStream = new DataOutputStream(byteStream);

		dataStream.write(source);
		dataStream.write(destAddress);
		dataStream.write(hopCount);
		dataStream.write(packetType);
		dataStream.writeInt(id);
		dataStream.write(data);

		if (packetType != Packet.BROADCAST)
			System.out.println("sent " + Integer.toHexString(id));

		socket.send(new DatagramPacket(byteStream.toByteArray(), byteStream.size(), inetAddress, PORT));

		forwardedPackets[forwardedPacketsIndex] = id;
		forwardedPacketsIndex = (forwardedPacketsIndex + 1) % forwardedPackets.length;
	}

	public ArrayList<Connection> getConnections() {
		return connections;
	}

	public void addListener(AdhocListener listener) {
		listeners.add(listener);
	}

	public void removeListener(AdhocListener listener) {
		listeners.remove(listener);
	}

	public byte getAddress() {
		return address;
	}

	public boolean isRunning() {
		return running;
	}

	public void close() {
		running = false;

		try {
			sendData(MULTICAST_ADDRESS, Packet.LEAVE, new byte[0]);
		} catch (IOException e) {
		}

		socket.close();
	}

	public interface AdhocListener {
		public void onReceive(Packet packet);

		public void newConnection(Connection connection);

		public void removedConnection(Connection connection);
	}
}
