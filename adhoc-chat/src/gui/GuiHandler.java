package gui;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Observable;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import adhoc.AdhocSocket;
import adhoc.AdhocSocket.AdhocListener;
import adhoc.Connection;
import adhoc.Packet;
import adhoc.PrivateMessageListener;
import adhoc.ReliableUDPSocket;

public class GuiHandler implements java.awt.event.ActionListener, AdhocListener, PrivateMessageListener {

	// the loginGUI
	private Login loginGUI;
	private JFrame frame;
	private JPanel panel, mainScreenPanel;
	private MainScreen mainScreen;
	private boolean main = false;
	private AdhocSocket socket;
	private ReliableUDPSocket UDPsocket;
	private HashMap<Byte, String> users = new HashMap<Byte, String>();
	private HashMap<String, Byte> addr = new HashMap<String, Byte>();
	
	public GuiHandler() {
		// JFrame
		frame = new JFrame();
		frame.setResizable(false);
		frame.setSize(370, 250);
		frame.setTitle("Adhoc ChatApp");
		frame.setLocationRelativeTo(null);
		
		// startup
		loginGUI = new Login();
		loginGUI.addController(this);
		panel = loginGUI.createPanel();
		
		frame.add(panel);
	    frame.setVisible(true);
	    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
	}
	
	/*
	 * Attempts to login, checks the username changes color to red if username
	 * bad, else go to chat main screen.
	 */
	private void tryLogin(String username) {
		// check username
		if (loginGUI.getUsername().matches("\\w{3,}+")) {
			
			try {
				socket = new AdhocSocket(loginGUI.getUsername());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			socket.addListener(this);
			UDPsocket = new ReliableUDPSocket();
			UDPsocket.registerListener(this);
			
			// remove the login panel and go to the mainScreen
			loginGUI.removeController(this);
			frame.remove(panel);
			frame.setSize(0, 0);
			frame.setSize(800, 700);
			main = true;
			mainScreen = new MainScreen(loginGUI.getUsername());
			mainScreenPanel = mainScreen.returnPanel();
			mainScreen.addChat("GroupChat", this);
			mainScreen.changeChat("GroupChat");
			//mainScreen.addChat("Willem", this);
			//mainScreen.addChat("Bob", this);
			//mainScreen.addChat("Michiel", this);
			//mainScreen.addChat("Ruben", this);
			frame.add(mainScreenPanel);
			frame.setLocationRelativeTo(null);
			frame.pack();
		} else {
			// give feedback that the username is bad
			loginGUI.setUsernameBad();
		}
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		//System.out.println("Event triggered");
		if (main && ((Component) e.getSource()).getName().contains("enter")) {
			String[] messageParts = ((Component) e.getSource()).getName().split("enter");
			String message = mainScreen.getMessage(messageParts[1]);
			if (!message.equals("") && message.trim().length() > 0 ) {
				// send chatmessage
				UDPsocket.sendChatMessage(addr.get(messageParts[1]), 0, message);
				Message newMessage;
				newMessage = mainScreen.addMessage(message, mainScreen.getUsername(), "#f22d2d", "#d10c0c", false, messageParts[1]);
				frame.pack();
				mainScreen.addSize(newMessage.getBounds().y, messageParts[1]);
				frame.pack();
				mainScreen.scrollDown(messageParts[1]);
			}
		} else {
			JButton source = (JButton) e.getSource();
			// if the user wants to login
			if (source.getName().equals("login")) {
				tryLogin(loginGUI.getUsername());
			} else if (source.getName().contains("send")) {
				String[] messageParts = ((Component) e.getSource()).getName().split("send");
				String message = mainScreen.getMessage(messageParts[1]);
				// send chatmessage
				UDPsocket.sendChatMessage(addr.get(messageParts[1]), 0, message);
				if (!message.equals("") && message.trim().length() > 0 ) {
					Message newMessage = mainScreen.addMessage(message, mainScreen.getUsername(), "#f22d2d", "#d10c0c", false, messageParts[1]);
					frame.pack();
					mainScreen.addSize(newMessage.getBounds().y, messageParts[1]);
					frame.pack();
					mainScreen.scrollDown(messageParts[1]);
				}
			} else {
				mainScreen.changeChat(source.getName());
			}
		}
	}
	
	@Override
	public void onReceive(Packet packet) {
		// TODO Auto-generated method stub
		System.out.println("JEEJ EEN PAKKET");
	}

	@Override
	public void newConnection(Connection connection) {
		// TODO Auto-generated method stub
		System.out.println("JEEJ EEN NIEUWE CONNECTIE");
		mainScreen.addChat(connection.name, this);
		users.put(connection.address, connection.name);
	}
	
	@Override
	public void removedConnection(Connection connection) {
		System.out.println("EEN CONNECTIE GING WEG");
		mainScreen.removeUser(connection.name);
	}
	
	public static void main(String[] args) {
		// guihandler
		GuiHandler handler = new GuiHandler();
		
	}

	@Override
	public void onReceiveMessage(byte sourceAddress, long timestampMillis,
			String message) {
		String username = users.get(sourceAddress);
		System.out.println("Received message from" + username);
		Message newMessage = mainScreen.addMessage(message, username, "#f22d2d", "#d10c0c", true, username);
		frame.pack();
		mainScreen.addSize(newMessage.getBounds().y, username);
		frame.pack();
		mainScreen.scrollDown(username);
	}
}
