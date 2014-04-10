package gui;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import util.GradientList;

import adhoc.AdhocSocket.AdhocListener;
import adhoc.Connection;
import adhoc.Packet;
import adhoc.ReliableSocket;

public class GuiHandler implements java.awt.event.ActionListener, AdhocListener{

	// the loginGUI
	private Login loginGUI;
	private JFrame frame;
	private JPanel panel, mainScreenPanel;
	private MainScreen mainScreen;
	private boolean main = false;
	private HashMap<Byte, String> users = new HashMap<Byte, String>();
	private HashMap<String, Byte> addr = new HashMap<String, Byte>();
	private HashMap<String, GradientList.Gradient> colors = new HashMap<String, GradientList.Gradient>();
	private ReliableSocket socket;
	private DateFormat df = new SimpleDateFormat("dd:MM:yy:HH:mm:ss");
	
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
			
//			try {
//				socket = new AdhocSocket(loginGUI.getUsername());
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//			socket.addListener(this);
			try {
				socket = new ReliableSocket(username);
				socket.addListener(this);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
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
				try {
					sendMessage(message, messageParts[1]);
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				//UDPsocket.sendChatMessage(addr.get(messageParts[1]), 0, message);
				Message newMessage;
				long timestamp = System.currentTimeMillis();
				Calendar cal = Calendar.getInstance();
				cal.setTimeInMillis(timestamp);
				String date = df.format(cal.getTime());
				newMessage = mainScreen.addMessage(message, mainScreen.getUsername(), "#f22d2d", "#d10c0c", false, messageParts[1], date);
				frame.pack();
				mainScreen.addSize(newMessage.getBounds().y, messageParts[1]);
				frame.pack();
				mainScreen.scrollDown(messageParts[1]);
			}
		} else {
			if (((Component) e.getSource()).getName().equals("loginkey")) {
				tryLogin(loginGUI.getUsername());
			} else {
				JButton source = (JButton) e.getSource();
				// if the user wants to login
				if (source.getName().equals("login")) {
					tryLogin(loginGUI.getUsername());
				} else if (source.getName().contains("send")) {
					String[] messageParts = ((Component) e.getSource()).getName().split("send");
					String message = mainScreen.getMessage(messageParts[1]);
					// send chatmessage
					if (!message.equals("") && message.trim().length() > 0 ) {
						try {
							sendMessage(message, messageParts[1]);
						} catch (IOException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}
						long timestamp = System.currentTimeMillis();
						Calendar cal = Calendar.getInstance();
						cal.setTimeInMillis(timestamp);
						String date = df.format(cal.getTime());
						//UDPsocket.sendChatMessage(addr.get(messageParts[1]), 0, message);
						Message newMessage = mainScreen.addMessage(message, mainScreen.getUsername(), "#f22d2d", "#d10c0c", false, messageParts[1], date);
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
	}

	@Override
	public void newConnection(Connection connection) {
		System.out.println("JEEJ EEN NIEUWE CONNECTIE");
		mainScreen.addChat(connection.name, this);
		users.put(connection.address, connection.name);
		addr.put(connection.name, connection.address);
		GradientList gradients = new GradientList();
		int index = colors.size();
		index = index % 6;
		
		GradientList.Gradient color = gradients.getGradient(index);
		colors.put(connection.name, color);
	}
	
	@Override
	public void removedConnection(Connection connection) {
		System.out.println("EEN CONNECTIE GING WEG");
		mainScreen.removeUser(connection.name);
		mainScreen.changeChat("GroupChat");
	}
	
	@Override
	public void onReceive(Packet packet) {
		System.out.println("Receive not ack packet");
		if (packet.getType() == (byte) 1) {
			try {
				System.out.println("Received a message");
				DataInputStream dataStream = new DataInputStream(new ByteArrayInputStream(packet.getData()));
				byte addr = packet.getSourceAddress();
				long timestamp = dataStream.readLong();
				
				Calendar cal = Calendar.getInstance();
				cal.setTimeInMillis(timestamp);
				String date = df.format(cal.getTime());
				
				String message = dataStream.readUTF();
				String username = users.get(addr);
				GradientList.Gradient color = colors.get(username);
				Message newMessage = mainScreen.addMessage(message, username, color.color1, color.color2, true, username, date);
				frame.pack();
				mainScreen.addSize(newMessage.getBounds().y, username);
				frame.pack();
				frame.revalidate();
				frame.repaint();
				mainScreen.scrollDown(username);
				frame.pack();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
		}
	}
	
	private void sendMessage(String inputMessage, String username) throws IOException {
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
		DataOutputStream dataStream = new DataOutputStream(byteStream);

		dataStream.writeLong(System.currentTimeMillis());
		dataStream.writeUTF(inputMessage);
		
		byte dest = addr.get(username);
		
		socket.send(dest, (byte) 1, byteStream.toByteArray());
		System.out.println("Send message");
	}
	
	public static void main(String[] args) {
		// guihandler
		GuiHandler handler = new GuiHandler();
	}
}
