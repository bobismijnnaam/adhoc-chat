package gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.HashMap;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

import net.miginfocom.swing.MigLayout;
import util.GradientPanel;

public class MainScreen {

	// the mainscreen panel
	private JPanel mainScreen, chatScreen, userlist, fullchat;
	private JScrollPane scrollpane;
	private HashMap<String, JPanel> chatScreens = new HashMap<String, JPanel>();
	private HashMap<String, JPanel> textFields = new HashMap<String, JPanel>();
	private HashMap<String, JTextField> realTextFields = new HashMap<String, JTextField>();
	private HashMap<String, JButton> users = new HashMap<String, JButton>();
	private HashMap<String, JScrollPane> scrollPanes = new HashMap<String, JScrollPane>();
	private String username;
	private JButton oldButton;
	private boolean init = false;

	public MainScreen(String inputUsername) {
		mainScreen = new JPanel(new MigLayout("insets 0"));
		mainScreen.setBackground(Color.DARK_GRAY);
		// create a chat
		fullchat = createFullChat("", null);
		mainScreen.add(fullchat, "split 2, w 540px, h 600px");
		username = inputUsername;
		userlist = new JPanel(new MigLayout("insets 0"));
		userlist.setBackground(Color.DARK_GRAY);
		Message user = new Message("<html><font color='white'>Online Users</font></html>", Color.LIGHT_GRAY,
				Color.DARK_GRAY);
		userlist.add(user, "span, w 160px, h 30px");
		mainScreen.add(userlist, "w 160px, h 600px");
	}

	/**
	 * returns username
	 */
	public String getUsername() {
		return username;
	}

	/**
	 * Change chat, it sets a different chat box
	 */
	public void changeChat(String name) {
		// JPanel newScreen = chatScreens.get(name);
		JPanel newTextField = textFields.get(name);
		Component[] components = fullchat.getComponents();
		for (int i = 0; i < components.length; i++) {
			fullchat.remove(components[i]);
		}
		System.out.println("Removed chat");
		if (!init) {
			init = true;
			oldButton = users.get(name);
			oldButton.setText("<html><font color='white'><u>" + name + "</u></font></html>");
		} else {
			oldButton.setText("<html><font color='white'>" + oldButton.getName() + "</font></html>");
		}
		users.get(name).setText("<html><font color='white'><u>" + name + "</u></font></html>");
		oldButton = users.get(name);
		// JLabel username = new JLabel("You're talking with " + name);
		Message username = new Message("<html><font color='white'>You're talking with <u>" + name
				+ "</u></font></html>", Color.LIGHT_GRAY, Color.DARK_GRAY);
		scrollpane = scrollPanes.get(name);
		scrollpane.getVerticalScrollBar().setUnitIncrement(16);
		fullchat.add(username, "span, w 530px, h 50px");
		fullchat.add(scrollpane, "span, w 530px, h 510px");
		fullchat.add(newTextField, "span, w 530px, h 50px");
		fullchat.revalidate();
		fullchat.repaint();
	}

	/**
	 * Adds a chatwindow and ads the user to the list
	 */
	public void addChat(String name, GuiHandler handler) {
		if (users.containsKey(name) && !users.get(name).isShowing()) {
			userlist.add(users.get(name), "span, w 160px, h 50px");
		}

		if (!chatScreens.containsKey(name)) {
			// create the chatwindow and textfield
			createFullChat(name, handler);
			// add the user to the list
			addUser(name, handler);
		}

		mainScreen.revalidate();
		mainScreen.repaint();
	}

	/**
	 * Removes the user out of the right window.
	 */
	public void removeUser(String name) {
		if (users.containsKey(name) && users.get(name).isShowing()) {
			userlist.remove(users.get(name));
			mainScreen.revalidate();
			mainScreen.repaint();
		}

	}

	/**
	 * Adds an user to the userlist
	 */
	private void addUser(String name, GuiHandler handler) {
		Message user = new Message("<html><font color='white'>" + name + "</font></html>", Color.LIGHT_GRAY, Color.GRAY);
		user.setName(name);
		user.addActionListener(handler);
		users.put(name, user);
		userlist.add(user, "span, w 160px, h 50px");
	}

	/**
	 * @Returns a chat screen with send button and puts username and chatpanel
	 *          in the map.
	 */
	private JPanel createFullChat(String name, GuiHandler handler) {
		JPanel fullChat = new JPanel(new MigLayout("insets 0"));
		fullChat.setBackground(Color.LIGHT_GRAY);
		JLabel username = new JLabel("You're talking with " + name);
		// create chatscreen
		chatScreen = createChatScreen();
		chatScreen.setPreferredSize(new Dimension(500, 500));
		scrollpane = new JScrollPane(chatScreen);
		scrollpane.getVerticalScrollBar().setUnitIncrement(16);
		JPanel sendButton = createSendButton(handler, name);
		fullChat.add(username, "span, w 530px, h 40px");
		fullChat.add(scrollpane, "span, w 530px, h 510px");
		fullChat.add(sendButton, "span, w 530px, h 50px");
		chatScreens.put(name, chatScreen);
		textFields.put(name, sendButton);
		scrollPanes.put(name, scrollpane);
		return fullChat;
	}

	private void addController(GuiHandler handler, JButton send, JTextField field) {
		send.addActionListener(handler);
		field.addActionListener(handler);
	} // addController()

	public String getMessage(String name) {
		String message = getTextField(name).getText();
		getTextField(name).setText("");
		return message;
	}

	public JTextField getTextField(String name) {
		return realTextFields.get(name);
	}

	/**
	 * Adds a message to a chatscreen
	 */
	public Message addMessage(String message, String username, String color1, String color2, boolean incoming,
			String group, String timeStamp) {
		Message newMessage = new Message("<html><font color=#ffffdd><font size=4><b>" + username + "</b></font><br />"
				+ formatMessage(message) + "<br /><font size=2>" + timeStamp + "</font></font></html>",
				Color.decode(color1), Color.decode(color2));
		JPanel chatScreen;
		chatScreen = chatScreens.get(group);
		if (incoming) {
			chatScreen.add(newMessage, "wrap 10, w 300px, h 50px");
		} else {
			chatScreen.add(newMessage, "wrap 10, w 300px, h 50px, gapx 200px");
		}
		mainScreen.revalidate();
		mainScreen.repaint();
		return newMessage;
	}

	/**
	 * Formats the string of the message
	 * 
	 * @return the formatted String
	 */
	private String formatMessage(String inputMessage) {
		String formattedMessage = inputMessage;
		return formattedMessage;
	}

	public void addSize(int size, String name) {
		if (size > 440) {
			JPanel chatScreen = chatScreens.get(name);
			Dimension old = chatScreen.getPreferredSize();
			old.height = size + 70;
			chatScreen.setPreferredSize(old);
			chatScreen.setPreferredSize(chatScreen.getPreferredSize());
			chatScreen.revalidate();
			mainScreen.revalidate();
			mainScreen.repaint();
		}
	}

	public void scrollDown(String name) {
		JScrollPane scrollpane = scrollPanes.get(name);
		JScrollBar vertical = scrollpane.getVerticalScrollBar();
		vertical.setValue(vertical.getMaximum());
		scrollpane.revalidate();
	}

	/**
	 * @Returns the send button and input bar
	 */
	private JPanel createSendButton(GuiHandler handler, String name) {
		GradientPanel sendButton = new GradientPanel(new MigLayout(), Color.DARK_GRAY, Color.DARK_GRAY);
		JTextField messageBox = new JTextField();
		messageBox.setName("enter" + name);
		messageBox.setBackground(Color.decode("#e2e2e2"));
		messageBox.setBorder(javax.swing.BorderFactory.createEmptyBorder());
		sendButton.add(messageBox, "split 2, w 400px, h 45px");
		Icon send = new ImageIcon("images/send.png");
		JButton actualSendButton = new JButton(send);
		actualSendButton.setName("send" + name);
		actualSendButton.setMargin(new Insets(0, 0, 0, 0));
		actualSendButton.setOpaque(false);
		actualSendButton.setContentAreaFilled(false);
		actualSendButton.setBorderPainted(false);
		actualSendButton.setBorder(null);
		realTextFields.put(name, messageBox);
		sendButton.add(actualSendButton, "w 100px, h 50px");
		addController(handler, actualSendButton, messageBox);
		return sendButton;
	}

	/**
	 * @returns a chatScreen
	 */
	private JPanel createChatScreen() {
		GradientPanel chatScreen = new GradientPanel(new MigLayout(), Color.DARK_GRAY, Color.DARK_GRAY);
		chatScreen.setSize(500, 500);
		chatScreen.setBackground(Color.LIGHT_GRAY);
		return chatScreen;
	}

	/**
	 * @Returns the mainScreen
	 */
	public JPanel returnPanel() {
		return mainScreen;
	}
}
