package gui;

import java.awt.Component;
import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import util.GradientList;
import util.Util;
import adhoc.AdhocSocket;
import adhoc.AdhocSocket.AdhocListener;
import adhoc.Connection;
import adhoc.Download;
import adhoc.FileTransferSocket;
import adhoc.FileTransferSocket.FileTransferListener;
import adhoc.Packet;
import adhoc.ReliableSocket;
import adhoc.Upload;
import crypto.Crypto;

//github.com/bobismijnnaam/adhoc-chat.git

public class GuiHandler implements ActionListener, AdhocListener, FileTransferListener {
	// the loginGUI
	private Login loginGUI;
	private JFrame frame;
	private JPanel panel, mainScreenPanel;
	private MainScreen mainScreen;

	// if in mainscreen
	private boolean main = false;

	// userlist, addresslists, colorlist
	private HashMap<Byte, String> users = new HashMap<Byte, String>();
	private HashMap<String, Byte> addr = new HashMap<String, Byte>();
	private HashMap<String, GradientList.Gradient> colors = new HashMap<String, GradientList.Gradient>();

	// socket
	private ReliableSocket socket;

	// data format
	private DateFormat df = new SimpleDateFormat("dd-MM-yy 'at' HH:mm:ss");
	private FileTransferSocket fileTransferSocket;

	public GuiHandler() {
		// JFrame
		frame = new JFrame();
		frame.setResizable(false);
		frame.setSize(370, 250);
		frame.setTitle("Adhoc ChatApp");
		frame.setLocationRelativeTo(null);

		// Add multicast address
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

		// on closing sent leave message
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				System.out.println("Closed");
				if (main) {
					socket.close();
					super.windowClosed(e);
				}
			}
		});
	}

	/**
	 * Triggered when an action in the UI is performed
	 */
	@Override
	public void actionPerformed(ActionEvent e) {
		// create color and timestamp
		GradientList list = new GradientList();
		final GradientList.Gradient color = list.sendColor();
		final long timestamp = System.currentTimeMillis();
		// if in main screen and enter is pressed in an arbritrary text field
		if (main && ((Component) e.getSource()).getName().contains("enter")) {
			String[] messageParts = ((Component) e.getSource()).getName().split("enter");
			String group = messageParts[1];
			// process the message
			processMessage(group, false, mainScreen.getUsername(), color, timestamp, "", false, false);
		} else {
			// check if it's an attempt to login
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
					processMessage(group, false, mainScreen.getUsername(), color, timestamp, "", false, false);
				} else if (source.getName().contains("upload")) {
					String[] messageParts = ((Component) e.getSource()).getName().split("upload");
					final String group = messageParts[1];
					final JFileChooser fc = new JFileChooser();
					fc.addActionListener(new ActionListener() {
						@Override
						public void actionPerformed(ActionEvent e) {
							System.out.println(fc.getSelectedFile());
							if (!(fc.getSelectedFile() == null)) {

								// store file local
								String filename = fc.getSelectedFile().getName();
								if (fc.getSelectedFile().length() > 100000) {
									JOptionPane.showMessageDialog(frame,
											"Please pick a file with a size less then 100kb", "File to big",
											JOptionPane.WARNING_MESSAGE);
								} else {
									boolean isImage = isImage(filename);

									try {
										copy(fc.getSelectedFile(), new File(FileTransferSocket.FOLDER_RECEIVE + "/"
												+ filename));
									} catch (IOException e1) {
										// TODO Auto-generated catch block
										e1.printStackTrace();
									}
									System.out.println("stored file");

									processMessage(group, false, mainScreen.getUsername(), color, timestamp, filename,
											true, isImage);

									byte dest = addr.get(group);

									try {
										fileTransferSocket.makeOffer(dest, fc.getSelectedFile().getAbsolutePath());
									} catch (Exception ex) {
										ex.printStackTrace();
									}
								}
							}
						}
					});
					fc.showOpenDialog(frame);
				} else if (source.getName().contains("file")) {
					String[] messageParts = ((Component) e.getSource()).getName().split("file");
					try {
						Desktop.getDesktop().open(new File(FileTransferSocket.FOLDER_RECEIVE + "/" + messageParts[1]));
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
				} else {
					mainScreen.changeChat(source.getName());
				}
			}
		}
	}

	/**
	 * Check if the image is usable for ICON messgae
	 * 
	 * @param name
	 *            , name + extension
	 * @return if the image is usable for ICON message
	 */
	public boolean isImage(String name) {
		String[] parts = name.split("\\.");
		if (parts.length != 2) {
			return false;
		}
		String ext = parts[1];
		String[] imgExt = { "png", "jpg", "jpeg", "gif" };
		for (int x = 0; x < imgExt.length; x++) {
			if (imgExt[x].equals(ext)) {
				System.out.println("image");
				return true;
			}
		}
		return false;
	}

	/**
	 * Attempts to login, checks the username changes color to red if username
	 * bad, else go to chat main screen.
	 * 
	 * @param username
	 *            - Name associated with address.
	 */
	private void tryLogin(String username) {
		// check username (longer than 3 characters only numbers and characters)
		if (loginGUI.getUsername().matches("^\\w{3,10}+$")) {

			// remove the login panel and go to the mainScreen
			loginGUI.removeController(this);
			frame.remove(panel);
			frame.revalidate();
			frame.repaint();
			main = true;

			// create the groupchat panel
			mainScreen = new MainScreen(loginGUI.getUsername());
			mainScreenPanel = mainScreen.returnPanel();
			mainScreen.addChat("GroupChat", this);
			mainScreen.changeChat("GroupChat");

			// switch to mainSreen, set window to center
			frame.add(mainScreenPanel);
			frame.pack();
			frame.setLocationRelativeTo(null);

			// attempt to create a reliablesocket
			try {
				socket = new ReliableSocket(username, Crypto.INSTANCE.getMyKey());
				socket.addListener(this);
				fileTransferSocket = new FileTransferSocket(socket.getAdhocSocket());
				fileTransferSocket.addListener(this);
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			// give feedback that the username is bad
			loginGUI.setUsernameBad();
		}
	}

	/**
	 * Processes the message, gives the correct parameters to sendmessage
	 * 
	 * @param group
	 *            - The group (chat) to send the message to
	 * @param incoming
	 *            - Wether it's an incoming message or not
	 * @param username
	 *            - The assosiated name with the address to display
	 * @param color
	 *            - The assosiated color with the username/adress
	 */
	private void processMessage(String group, boolean incoming, String username, GradientList.Gradient color,
			long timestamp, String message, boolean file, boolean img) {
		// retrieve message from textfield in associated group if it's an
		// outgoing message
		if (!incoming) {
			if (!file)
				message = mainScreen.getMessage(group);
		}
		if (!message.equals("") && message.trim().length() > 0) {
			if (!incoming) {
				try {
					// send message to group (or individual)
					if (!file)
						sendMessage(message, group);
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}

			// create a timestamp and a new message
			Calendar cal = Calendar.getInstance();
			cal.setTimeInMillis(timestamp);
			String date = df.format(cal.getTime());
			Message newMessage = mainScreen.addMessage(Util.makeHtmlSafe(message), username, color.color1,
					color.color2, incoming, group, date, file, img);
			mainScreen.addFileOpener(newMessage, this);
			frame.pack();
			mainScreen.addSize(newMessage.getBounds().y + newMessage.getBounds().height, group);
			System.out.println("new max y: " + (newMessage.getBounds().y + newMessage.getBounds().height + 10));
			frame.pack();
			mainScreen.scrollDown(group, (newMessage.getBounds().y + newMessage.getBounds().height + 10));
		}
	}

	/**
	 * Triggered when a new connection is esthablished, adds a new chat and user
	 * to list of neccesary
	 * 
	 * @param connection
	 *            - The connection received from the ReliableSocket
	 */
	@Override
	public void newConnection(Connection connection) {
		// adds a chat for the new connection if needed and add the connection
		// and username to the hashmaps.
		mainScreen.addChat(connection.name, this);
		users.put(connection.address, connection.name);
		addr.put(connection.name, connection.address);
		GradientList gradients = new GradientList();
		int index = colors.size();
		// index = index % gradients.getSize();
		index = Math.abs(connection.hashCode() % gradients.getSize());

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
	 * 
	 * @param connection
	 *            - The connection received from the ReliableSocket
	 */
	@Override
	public void removedConnection(Connection connection) {
		System.out.println("EEN CONNECTIE GING WEG");
		mainScreen.removeUser(connection.name);
		// mainScreen.changeChat("GroupChat");
	}

	/**
	 * Triggered when a packet is received
	 * 
	 * @param packet
	 *            - packet received from the ReliableSocket
	 * @see adhoc.AdhocSocket.AdhocListener#onReceive(adhoc.Packet)
	 */
	@Override
	public void onReceive(Packet packet) {
		// if it's an chat packet
		if (packet.getType() == Packet.TYPE_CHAT) {
			try {
				System.out.println("Received a message");
				byte[] data = null;
				byte addr = packet.getDestAddress();
				boolean isGroupChat = false;

				// if it's a broadcast (groupchat)
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

					mainScreen.addNotification(dest);
					processMessage(dest, true, username, color, timestamp, message, false, false);
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
	 *            - username (group) to send to
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
			socket.send(dest, Packet.TYPE_CHAT, byteStream.toByteArray());
		} else {
			socket.send(dest, Packet.TYPE_CHAT, Crypto.INSTANCE.encrypt(dest, byteStream.toByteArray()));
		}
	}

	/**
	 * starts the whole program!
	 */
	public static void main(String[] args) {
		// guihandler
		new GuiHandler();
	}

	@Override
	public void onReceiveDownloadOffer(Download downloadOffer) {
		int n = JOptionPane.showConfirmDialog(frame, "Allow " + users.get(downloadOffer.getAddress())
				+ " to send a file?", "File offer", JOptionPane.YES_NO_OPTION);
		System.out.println(n);
		if (n == 0) {
			fileTransferSocket.respondOffer(downloadOffer, true);
		} else {
			fileTransferSocket.respondOffer(downloadOffer, false);
		}
	}

	@Override
	public void onFileTransferComplete(Download download) {
		System.out.println(download.getTransferSpeed());
		File file = new File(FileTransferSocket.FOLDER_RECEIVE + "/" + download.getFilename());
		boolean isImage = isImage(download.getFilename());
		final long timestamp = System.currentTimeMillis();
		System.out.println("I received: " + download.getFilename() + " From: " + users.get(download.getAddress()));
		processMessage(users.get(download.getAddress()), true, users.get(download.getAddress()),
				colors.get(users.get(download.getAddress())), timestamp, download.getFilename(), true, isImage);
		mainScreen.addNotification(users.get(download.getAddress()));
	}

	@Override
	public void onOfferRejected(Upload upload) {
		JOptionPane.showMessageDialog(frame, "Your offer to send " + upload.getFilename() + " was declined.",
				"Offer declined", JOptionPane.WARNING_MESSAGE);
	}

	public void copy(File src, File dst) throws IOException {
		InputStream in = new FileInputStream(src);
		OutputStream out = new FileOutputStream(dst);

		// Transfer bytes from in to out
		byte[] buf = new byte[1024];
		int len;
		while ((len = in.read(buf)) > 0) {
			out.write(buf, 0, len);
		}
		in.close();
		out.close();
	}

}
