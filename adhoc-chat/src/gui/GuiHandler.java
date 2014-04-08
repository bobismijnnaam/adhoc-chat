package gui;

import java.awt.event.ActionEvent;
import java.util.Observable;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

public class GuiHandler implements java.util.Observer, java.awt.event.ActionListener {

	// the loginGUI
	private Login loginGUI;
	private JFrame frame;
	private JPanel panel, mainScreenPanel;
	private MainScreen mainScreen;
	
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
		if (loginGUI.getUsername().matches("\\w{4,}+")) {
			// remove the login panel and go to the mainScreen
			loginGUI.removeController(this);
			frame.remove(panel);
			frame.setSize(0, 0);
			frame.setSize(800, 700);
			mainScreen = new MainScreen(loginGUI.getUsername());
			mainScreenPanel = mainScreen.returnPanel();
			mainScreen.addController(this);
			frame.add(mainScreenPanel);
			frame.setLocationRelativeTo(null);
		} else {
			// give feedback that the username is bad
			loginGUI.setUsernameBad();
		}
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		JButton source = (JButton) e.getSource();
		System.out.println(source.getLocation());
		System.out.println(source.getName());
		// if the user wants to login
		if (source.getName().equals("login")) {
			System.out.println("User wants to login with username: " + loginGUI.getUsername());
			tryLogin(loginGUI.getUsername());
		} else if (source.getName().equals("send")) {
			mainScreen.addMessage(mainScreen.getMessage());
		}
	}
	
	@Override
	public void update(Observable arg0, Object arg1) {
		System.out.println("Observered a change");
	}
	
	public static void main(String[] args) {
		GuiHandler gui = new GuiHandler();
	}
}
