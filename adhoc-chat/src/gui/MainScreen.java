package gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.ScrollPane;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import util.GradientPanel;
import net.miginfocom.swing.MigLayout;

public class MainScreen {
	
	// the mainscreen panel
	private JPanel mainScreen, chatScreen;
	private JButton actualSendButton;
	private JTextField messageBox;
	private JScrollPane scrollpane;
	private int increased = 0;
	
	public MainScreen(String inputUsername) {
		mainScreen = new JPanel(new MigLayout());
		JLabel username = new JLabel("Hey " + inputUsername + " welcome to Ad-Hoc chat.");
		mainScreen.add(username, "span");
		
		// create chatscreen
		chatScreen = createChatScreen();
		chatScreen.setPreferredSize(new Dimension(500, 500));
		//chatScreen.setPreferredSize(chatScreen.getPreferredSize());
		scrollpane = new JScrollPane(chatScreen);
		scrollpane.getVerticalScrollBar().setUnitIncrement(16);
		mainScreen.add(scrollpane, "span, w 530px, h 500px");
		
		// create the send button
		JPanel sendButton = createSendButton();
		mainScreen.add(sendButton, "span, w 530px, h 50px");
	}
	
	public void addController(GuiHandler handler){
        actualSendButton.addActionListener(handler);
        messageBox.addActionListener(handler);
	} //addController()
	
	public String getMessage() {
		String message = messageBox.getText();
		messageBox.setText("");
		return message;
	}
	
	public JTextField getTextField() {
		return messageBox;
	}
	
	/*
	 * Adds a message to a chatscreen
	 */
	public Message addMessage(String message) {
		Message newMessage = new Message("<html>"
                + "<font color=#ffffdd><font size=4><b>Bob</b></font><br />" + message + "<br /><font size=2>10:11 4/4/2014</font></font></html>", Color.decode("#ffe509"), Color.decode("#ccb410"));
		chatScreen.add(newMessage, "wrap 10, w 300px, h 50px, gapx 200px");
		mainScreen.revalidate();
		mainScreen.repaint();
		return newMessage;
	}
	
	public void addSize(int size) {
		if (size > 440) {
			Dimension old = chatScreen.getPreferredSize();
			old.height = size + 70;
			chatScreen.setPreferredSize(old);
			chatScreen.setPreferredSize(chatScreen.getPreferredSize());
			chatScreen.revalidate();
			mainScreen.revalidate();
			mainScreen.repaint();
		} 
	}
	
	public void scrollDown() {
		JScrollBar vertical = scrollpane.getVerticalScrollBar();
		vertical.setValue( vertical.getMaximum());
		scrollpane.revalidate();
	}
	
	/*
	 * Returns the send button and input bar
	 */
	private JPanel createSendButton() {
		GradientPanel sendButton = new GradientPanel(new MigLayout(), Color.DARK_GRAY, Color.DARK_GRAY);
		messageBox = new JTextField();
		messageBox.setBackground(Color.decode("#e2e2e2"));
		messageBox.setBorder(javax.swing.BorderFactory.createEmptyBorder());
		sendButton.add(messageBox, "split 2, w 400px, h 45px");
		Icon send = new ImageIcon("images/send.png");
		actualSendButton = new JButton(send);
		actualSendButton.setName("send");
		actualSendButton.setMargin(new Insets(0, 0, 0, 0));
		actualSendButton.setOpaque(false);
		actualSendButton.setContentAreaFilled(false);
		actualSendButton.setBorderPainted(false);
		actualSendButton.setBorder(null);
		sendButton.add(actualSendButton, "w 100px, h 50px");
		return sendButton;
	}
	
	/*
	 * returns a chatScreen
	 */
	private JPanel createChatScreen() {
		GradientPanel chatScreen = new GradientPanel(new MigLayout(), Color.DARK_GRAY, Color.DARK_GRAY);
		chatScreen.setSize(500, 500);
		chatScreen.setBackground(Color.LIGHT_GRAY);
//		JButton chatMessage = new JButton("<html>"
//                 + "<font color=#ffffdd><font size=5><b>Michiel</b></font><br />This is a sample message send by me.<br /><font size=2>10:11 4/4/2014</font></font></html>");
//		chatMessage.setFocusPainted(false);
//		chatMessage.setMargin(new Insets(10, 10, 10, 10));
//		chatMessage.setOpaque(false);
//		//chatMessage.setContentAreaFilled(false);
//		chatMessage.setBorderPainted(false);
//		chatMessage.setBorder(null);
//		chatScreen.add(chatMessage, "span, w 150px, h 50px");
		
		Message newMessage = new Message("<html>"
                 + "<font color=#ffffdd><font size=4><b>Bob</b></font><br />This is a sample message send by me.<br /><font size=2>10:11 4/4/2014</font></font></html>", Color.decode("#ffe509"), Color.decode("#ccb410"));
		Message newMessage2 = new Message("<html>"
                + "<font color=#ffffdd><font size=4><b>Michiel</b></font><br />This is a sample message send by me.<br /><font size=2>10:11 4/4/2014</font></font></html>", Color.decode("#f22d2d"), Color.decode("#d10c0c"));
		Message newMessage3 = new Message("<html>"
                + "<font color=#ffffdd><font size=4><b>Michiel</b></font><br />This is a sample message send by me. This is a sample message send by me. This is a sample message send by me.<br /><font size=2>10:11 4/4/2014</font></font></html>", Color.decode("#f22d2d"), Color.decode("#d10c0c"));
		Message newMessage4 = new Message("<html>"
                + "<font color=#ffffdd><font size=4><b>Bob</b></font><br />This is a sample message send by me.<br /><font size=2>10:11 4/4/2014</font></font></html>", Color.decode("#ffe509"), Color.decode("#ccb410"));
		chatScreen.add(newMessage2, "wrap 10, w 300px, h 50px");
		chatScreen.add(newMessage, "wrap 10, w 300px, h 50px, gapx 200px");
		chatScreen.add(newMessage3, "wrap 10, w 300px, h 50px");
		chatScreen.add(newMessage4, "wrap 10, w 300px, h 50px, gapx 200px");
		return chatScreen;
	}
	
	/*
	 * Returns the mainScreen
	 */
	public JPanel returnPanel() {
		return mainScreen;
	}
}
