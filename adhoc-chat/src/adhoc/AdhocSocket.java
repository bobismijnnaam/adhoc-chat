package adhoc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.ArrayList;

public class AdhocSocket implements Runnable {
	private static final String ADDRESS = "224.42.42.42";
	private static final int PORT = 4001;
	protected static final long BROADCAST_TIME = 1000;
	
	protected static final byte BROADCAST_TYPE = 0;
	protected static final byte MULTICAST_ADDRESS = -1;
	
	private MulticastSocket socket;
	
	private ArrayList<AdhocListener> listeners = new ArrayList<AdhocListener>();
	private boolean running = true;
	private byte address;
	private final String name;
	
	public static void main(String[] args) throws IOException {
		new AdhocSocket("test");
	}
	
	public AdhocSocket(final String name) throws IOException {
		this.name = name;
		
		socket = new MulticastSocket(PORT);
		
		socket.joinGroup(InetAddress.getByName(ADDRESS));
		
		new Thread(this).start();
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				while (running) {
					try {
						Thread.sleep(BROADCAST_TIME);
					} catch (InterruptedException e) {
					}
					
					ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
					DataOutputStream dataStream = new DataOutputStream(byteStream);
					
					try {
						dataStream.writeUTF(name);
						
						sendData(MULTICAST_ADDRESS, BROADCAST_TYPE, byteStream.toByteArray());
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}).start();
	}
	
	public void run() {
		while (running) {
			try {
				byte[] buffer = new byte[1500];
				DatagramPacket p = new DatagramPacket(buffer, buffer.length);
				socket.receive(p);
				
				onReceive(buffer);
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
		
		byte[] data = new byte[byteStream.available()];
		dataStream.read(data);
		
		Packet packet = new Packet(source, dest, hopCount, type, data);
		
		if (dest != address) {
			if (source != address) {
				if (hopCount > 0) {
					hopCount--;
					sendData(source, dest, hopCount, type, data);
				}
			}
		} else {
			if (hopCount == 0) {
				for (AdhocListener listener : listeners) {
					listener.onReceive(packet);
				}
			}
		}
	}
	
	public void sendData(byte destAddress, byte packetType, byte[] data) throws IOException {
		sendData(address, destAddress, (byte) 8, packetType, data);
	}
	
	public void sendData(byte source, byte destAddress, byte hopCount, byte packetType, byte[] data) throws IOException {
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
		DataOutputStream dataStream = new DataOutputStream(byteStream);
		
		dataStream.write(source);
		dataStream.write(destAddress);
		dataStream.write(hopCount);
		dataStream.write(packetType);
		dataStream.write(data);
		
		socket.send(new DatagramPacket(byteStream.toByteArray(), byteStream.size()));
	}
	
	public void addListener(AdhocListener listener) {
		listeners.add(listener);
	}
	
	public void removeListener(AdhocListener listener) {
		listeners.remove(listener);
	}
	
	public void close() {
		running = false;
		
		//TODO: send leave message?
		
		socket.close();
	}
	
	public interface AdhocListener {
		public void onReceive(Packet packet);
	}
}
