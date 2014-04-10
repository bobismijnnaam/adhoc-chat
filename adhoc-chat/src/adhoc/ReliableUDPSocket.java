package adhoc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import adhoc.AdhocSocket.AdhocListener;

/**
 * ReliableUDPSocket can reliably send packets to a destination
 * 
 * @author willem
 * 
 */
public class ReliableUDPSocket implements Runnable, AdhocListener {

	/**
	 * unackedPackets - List of packets sent from 'this' client, not yet
	 * confirmed to be received
	 */
	private List<ChatPacket> unackedPackets = new ArrayList<ChatPacket>();

	/**
	 * toBeAcked - List of acks needed to be sent back to message's the origin
	 */
	private List<ChatPacket> toBeAcked = new ArrayList<ChatPacket>();

	/**
	 * listeners - registered listeners for chat-messages
	 */
	private List<UDPSocketListener> listeners = new ArrayList<UDPSocketListener>();

	/**
	 * nextSeqNr - sequence number to use for the next packet to be sent
	 */
	private static int nextSeqNr = 0;

	/**
	 * socket - lower layer (see {@link AdhocSocket})
	 */
	private AdhocSocket socket;

	public ReliableUDPSocket(String username) {
		try {
			socket = new AdhocSocket(username);
			socket.addListener(this);
		} catch (IOException e) {
			e.printStackTrace();
		}
		new Thread(this).start();

	}

	/**
	 * Send a chat
	 * 
	 * @param dstAddress
	 * @param timeStamp
	 * @param message
	 */
	public void sendChatMessage(byte dstAddress, long timeStamp, String message) {
		ChatPacket chatPacket = new ChatPacket(timeStamp, message, dstAddress, nextSeqNr++);
		synchronized (unackedPackets) {
			unackedPackets.add(chatPacket);
		}
	}

	// public static void main(String[] args) {
	//
	// UDPPacket chatPacket = new UDPPacket((byte) 1, (byte) 4, 123);
	// byte[] toSend = chatPacket.compileData();
	//
	// UDPPacket udpPacket = UDPPacket.parse(toSend);
	// if (udpPacket.getType() == UDPPacket.TYPE_CHAT) {
	// System.out.println(((ChatPacket) udpPacket).getMessage());
	// }
	// System.out.println(udpPacket.getType());
	//
	// }

	/**
	 * Add listener
	 * 
	 * @param l
	 */
	public void registerListener(UDPSocketListener l) {
		listeners.add(l);
	}

	@Override
	public void run() {
		while (true) {
			long now = System.currentTimeMillis();
			synchronized (unackedPackets) {
				for (ChatPacket packet : unackedPackets) {
					if (packet.shouldSend(now)) {
						try {
							System.out.println("Sending Chat ");
							socket.sendData(packet.getDstAddress(), (byte) 0, packet.compileData());
							packet.onSend();
						} catch (IOException e) {
							socket.close();
							e.printStackTrace();
						}
					}
				}
			}
			synchronized (toBeAcked) {
				for (Iterator<ChatPacket> iterator = toBeAcked.iterator(); iterator.hasNext();) {
					ChatPacket p = (ChatPacket) iterator.next();
					try {
						System.out.println("SENDING ACK " + p.getType());
						socket.sendData(p.getDstAddress(), (byte) 1, p.compileData());
						iterator.remove();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

	@Override
	public void onReceive(Packet packet) {

		// get
		UDPPacket receivedPacket = UDPPacket.parse(packet.getData());

		if (receivedPacket.getType() == UDPPacket.TYPE_CHAT) {
			ChatPacket chatPacket = (ChatPacket) receivedPacket;
			for (UDPSocketListener l : listeners) {
				l.onReceiveMessage(packet.getSourceAddress(), chatPacket.getTimeStamp(), chatPacket.getMessage());
			}

			synchronized (toBeAcked) {
				toBeAcked.add(chatPacket);
			}
		}

		else {
			System.out.println(" RECEIVED ACK : !!!!!!!!!!!!!!!");
			synchronized (unackedPackets) {
				boolean success = unackedPackets.remove(receivedPacket);
				System.out.println(" SUCCESS " +success);
			}
		}

	}

	@Override
	public void newConnection(Connection connection) {
		for (UDPSocketListener listener : listeners) {
			listener.newConnection(connection);
		}
	}

	@Override
	public void removedConnection(Connection connection) {
		for (UDPSocketListener listener : listeners) {
			listener.removedConnection(connection);
		}
	};
}
