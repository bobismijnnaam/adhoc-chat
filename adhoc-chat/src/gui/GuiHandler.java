package gui;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import util.GradientList;
import adhoc.AdhocSocket;
import adhoc.AdhocSocket.AdhocListener;
import adhoc.Connection;
import adhoc.Packet;
import adhoc.ReliableSocket;
import crypto.Crypto;

public class GuiHandler implements ActionListener, AdhocListener {
	// the loginGUI
	private Login loginGUI;
	private JFrame frame;
	private JPanel panel, mainScreenPanel;
	private MainScreen mainScreen;

	// if in mainscreen
	private boolean main = false;

	// userlist, adreslists, colorlist
	private HashMap<Byte, String> users = new HashMap<Byte, String>();
	private HashMap<String, Byte> addr = new HashMap<String, Byte>();
	private HashMap<String, GradientList.Gradient> colors = new HashMap<String, GradientList.Gradient>();

	// socket
	private ReliableSocket socket;

	// data format
	private DateFormat df = new SimpleDateFormat("dd-MM-yy 'at' HH:mm:ss");

	public GuiHandler() {
		// JFrame
		frame = new JFrame();
		frame.setResizable(false);
		frame.setSize(370, 250);
		frame.setTitle("Adhoc ChatApp");
		frame.setLocationRelativeTo(null);

		users.put(AdhocSocket.MULTICAST_ADDRESS, "GroupChat");
		addr.put("GroupChat", AdhocSocket.MULTICAST_ADDRESS);

		// startup
		loginGUI = new Login();
		loginGUI.addController(this);
		panel = loginGUI.createPanel();

		// add the mainpanel
		frame.add(panel);
		frame.setVisible(true);
		frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
	}

	/**
	 * Attempts to login, checks the username changes color to red if username
	 * bad, else go to chat main screen.
	 */
	private void tryLogin(String username) {
		// check username (longer than 3 characters only numbers and characters)
		if (loginGUI.getUsername().matches("\\w{3,}+")) {

			// remove the login panel and go to the mainScreen
			loginGUI.removeController(this);
			frame.remove(panel);
			frame.setSize(0, 0);
			frame.setSize(800, 700);
			main = true;

			// create the groupchat panel
			mainScreen = new MainScreen(loginGUI.getUsername());
			mainScreenPanel = mainScreen.returnPanel();
			mainScreen.addChat("GroupChat", this);
			mainScreen.changeChat("GroupChat");
			frame.add(mainScreenPanel);
			frame.pack();
			frame.setLocationRelativeTo(null);

			try {
				socket = new ReliableSocket(username, Crypto.INSTANCE.getMyKey());
				socket.addListener(this);
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			// give feedback that the username is bad
			loginGUI.setUsernameBad();
		}
	}

	/**
	 * Triggered when an action in the UI is performed
	 */
	@Override
	public void actionPerformed(ActionEvent e) {
		// create color and timestamp
		GradientList list = new GradientList();
		GradientList.Gradient color = list.sendColor();
		long timestamp = System.currentTimeMillis();
		if (main && ((Component) e.getSource()).getName().contains("enter")) {
			String[] messageParts = ((Component) e.getSource()).getName().split("enter");
			String group = messageParts[1];
			// process the message
			processMessage(group, false, mainScreen.getUsername(), color, timestamp, "");
		} else {
			if (((Component) e.getSource()).getName().equals("loginkey")) {
				// try login
				tryLogin(loginGUI.getUsername());
			} else {
				JButton source = (JButton) e.getSource();
				// if the user wants to login
				if (source.getName().equals("login")) {
					tryLogin(loginGUI.getUsername());
				} else if (source.getName().contains("send")) {
					String[] messageParts = ((Component) e.getSource()).getName().split("send");
					String group = messageParts[1];
					// process the message
					processMessage(group, false, mainScreen.getUsername(), color, timestamp, "");
				} else {
					mainScreen.changeChat(source.getName());
				}
			}
		}
	}

	/**
	 * Processes the message, gives the correct parameters to sendmessage
	 */
	private void processMessage(String group, boolean incoming, String username, GradientList.Gradient color,
			long timestamp, String message) {
		// retrieve message from textfield in associated group
		if (!incoming)
			message = mainScreen.getMessage(group);
		if (!message.equals("") && message.trim().length() > 0) {
			if (!incoming) {
				try {
					// send message to group (or individual)
					sendMessage(message, group);
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}

			Message newMessage;
			Calendar cal = Calendar.getInstance();
			cal.setTimeInMillis(timestamp);
			String date = df.format(cal.getTime());
			newMessage = mainScreen.addMessage(message, username, color.color1, color.color2, incoming, group, date);
			frame.pack();
			mainScreen.addSize(newMessage.getBounds().y, group);
			System.out.println(newMessage.getBounds().y);
			frame.pack();
			mainScreen.scrollDown(group);
		}
	}

	/**
	 * Triggered when a new connection is esthablished, adds a new chat and user
	 * to list of neccesary
	 */
	@Override
	public void newConnection(Connection connection) {
		mainScreen.addChat(connection.name, this);
		users.put(connection.address, connection.name);
		addr.put(connection.name, connection.address);
		GradientList gradients = new GradientList();
		int index = colors.size();
		index = index % 6;

		// picks a color from the gradientcolor list and associates it with the
		// username
		if (!colors.containsKey(connection.name)) {
			GradientList.Gradient color = gradients.getGradient(index);
			colors.put(connection.name, color);
			System.out.println(colors.size());
		}
	}

	/**
	 * Triggered when a connection is removed, removes user and chat from UI
	 */
	@Override
	public void removedConnection(Connection connection) {
		System.out.println("EEN CONNECTIE GING WEG");
		mainScreen.removeUser(connection.name);
		// because bob wants to reread messages from his lovers
		// mainScreen.changeChat("GroupChat");
	}

	/**
	 * Triggered when a packet is received
	 * 
	 * @see adhoc.AdhocSocket.AdhocListener#onReceive(adhoc.Packet)
	 */
	@Override
	public void onReceive(Packet packet) {
		System.out.println("Receive not ack packet");
		if (packet.getType() == Packet.CHAT) {
			try {
				System.out.println("Received a message");
				byte[] data = null;
				byte addr = packet.getDestAddress();
				boolean isGroupChat = false;

				// if it's a broadcast
				if (addr == AdhocSocket.MULTICAST_ADDRESS) {
					data = packet.getData();
					isGroupChat = true;
				} else {
					data = Crypto.INSTANCE.decrypt(packet.getData());
				}

				DataInputStream dataStream = new DataInputStream(new ByteArrayInputStream(data));
				long timestamp = dataStream.readLong();

				String message = dataStream.readUTF();
				String username = users.get(packet.getSourceAddress());
				GradientList.Gradient color = colors.get(username);
				String dest = username;

				// drop if connection was not yet esthablished
				if (users.containsKey(packet.getSourceAddress())) {

					if (isGroupChat)
						dest = "GroupChat";
					System.out.println(message + username + dest);

					processMessage(dest, true, username, color, timestamp, message);
					mainScreen.scrollDown(dest);
				}

			} catch (IOException e) {
				e.printStackTrace();
			}

		}
	}

	/**
	 * Sends a message
	 * 
	 * @param inputMessage
	 *            - message to send
	 * @param username
	 *            - username to send to
	 * @throws IOException
	 *             if something goes wrong
	 */
	private void sendMessage(String inputMessage, String username) throws IOException {
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
		DataOutputStream dataStream = new DataOutputStream(byteStream);

		byte dest = addr.get(username);

		dataStream.writeLong(System.currentTimeMillis());
		dataStream.writeUTF(inputMessage);

		if (username.equals("GroupChat")) {
			socket.send(dest, Packet.CHAT, byteStream.toByteArray());
		} else {
			socket.send(dest, Packet.CHAT, Crypto.INSTANCE.encrypt(dest, byteStream.toByteArray()));
		}
	}

	/**
	 * starts the whole program!
	 */
	public static void main(String[] args) {
		// guihandler
		new GuiHandler();
	}
}
