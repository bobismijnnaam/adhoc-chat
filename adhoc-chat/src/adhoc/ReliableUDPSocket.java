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
	private List<UDPPacket> unackedPackets = new ArrayList<UDPPacket>();

	/**
	 * toBeAcked - List of acks needed to be sent back to message's the origin
	 */
	private List<UDPPacket> toBeAcked = new ArrayList<UDPPacket>();

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
	 * Add to sending queue
	 * 
	 * @param dstAddress
	 *            -
	 * @param data
	 *            -- underlying data (e.g. textchat)
	 */
	public void sendReliable(UDPPacket packet) {
		synchronized (unackedPackets) {
			unackedPackets.add(packet);
		}
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
		sendReliable(chatPacket);
	}

//	public static void main(String[] args) {
//	
//		ChatPacket chatPacket = new ChatPacket(System.currentTimeMillis(), "Hoi Ruben", (byte) 2, nextSeqNr++);
//		byte[] toSend = chatPacket.compileData();
//		
//		UDPPacket udpPacket = UDPPacket.parse(toSend);
//		System.out.println(udpPacket.getType());
//		
//
//	}

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
				for (UDPPacket packet : unackedPackets) {
					if (packet.shouldSend(now)) {
						try {
							// System.out.println("SEND PKT=" + packet.seqNr +
							// " ATTEMPT=" + packet.attemptCount);
							socket.sendData(packet.getDstAddress(), packet.getType(), packet.compileData());
							packet.onSend();
						} catch (IOException e) {
							socket.close();
							e.printStackTrace();
						}
					}
				}
			}
			synchronized (toBeAcked) {
				for (Iterator<UDPPacket> iterator = toBeAcked.iterator(); iterator.hasNext();) {
					UDPPacket p = (UDPPacket) iterator.next();
					try {
						socket.sendData(p.getDstAddress(), p.getType(), p.compileData());
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

		else if (receivedPacket.getType() == UDPPacket.TYPE_ACK) {
			synchronized (unackedPackets) {
				boolean success = unackedPackets.remove(receivedPacket);
				System.out.println(" Packet Acked succes? : " + success);
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
