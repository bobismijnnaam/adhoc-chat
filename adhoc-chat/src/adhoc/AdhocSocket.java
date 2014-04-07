package adhoc;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.ArrayList;

public class AdhocSocket implements Runnable{
	private static final String ADDRESS = "224.42.42.42";
	private static final int PORT = 4001;
	
	private MulticastSocket socket;
	
	private ArrayList<AdhocListener> listeners = new ArrayList<AdhocListener>();
	private boolean running = true;

	public AdhocSocket() throws IOException{
		socket = new MulticastSocket(PORT);
		
		socket.joinGroup(InetAddress.getByName(ADDRESS));
		
		new Thread(this).start();
	}
	
	public void run() {
		while(running){
			try {
				byte[] buffer = new byte[1500];
				DatagramPacket p  = new DatagramPacket(buffer, buffer.length);
				socket.receive(p);
				
				for(AdhocListener listener : listeners){
					listener.onReceive(new DataInputStream(new ByteArrayInputStream(buffer)));
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void addListener(AdhocListener listener){
		listeners.add(listener);
	}
	
	public void removeListener(AdhocListener listener){
		listeners.remove(listener);
	}
	
	public void close(){
		running  = false;
		
		//TODO: send leave message?
		
		socket.close();
	}
	
	public interface AdhocListener{
		public void onReceive(DataInputStream dataInputStream);
	}
}
