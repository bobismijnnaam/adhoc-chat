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
	
	private MulticastSocket socket;
	
	private ArrayList<AdhocListener> listeners = new ArrayList<AdhocListener>();
	private boolean running = true;
	private byte address;
	
	public AdhocSocket() throws IOException {
		socket = new MulticastSocket(PORT);
		
		socket.joinGroup(InetAddress.getByName(ADDRESS));
		
		new Thread(this).start();
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
	
	private void onReceive(byte[] data) throws IOException {
		ByteArrayInputStream byteStream = new ByteArrayInputStream(data);
		DataInputStream dataStream = new DataInputStream(byteStream);
		
		byte source = dataStream.readByte();
		byte dest = dataStream.readByte();
		byte hopCount = dataStream.readByte();
		
		if (dest != address) {
			if (source != address) {
				if (hopCount > 0) {
					hopCount--;
					sendData(source, dest, hopCount, data);
				}
			}
		} else {
			if (hopCount == 0) {
				for (AdhocListener listener : listeners) {
					listener.onReceive(new DataInputStream(new ByteArrayInputStream(data)));
				}
			}
		}
	}
	
	public void sendData(byte destAddress, byte[] data) throws IOException {
		sendData(address, destAddress, (byte) 64, data);
	}
	
	public void sendData(byte source, byte destAddress, byte hopCount, byte[] data) throws IOException {
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
		DataOutputStream dataStream = new DataOutputStream(byteStream);
		
		dataStream.write(source);
		dataStream.write(destAddress);
		dataStream.write(64);
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
		public void onReceive(DataInputStream dataInputStream);
	}
}
