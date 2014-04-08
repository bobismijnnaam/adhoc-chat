package gui;

import javax.swing.JLabel;
import javax.swing.JPanel;

public class MainScreen {
	
	// the mainscreen panel
	private JPanel mainScreen;
	
	public MainScreen(String inputUsername) {
		mainScreen = new JPanel();
		JLabel username = new JLabel("Hey " + inputUsername + " welcome to Ad-Hoc chat.");
		mainScreen.add(username);
	}
	
	/*
	 * Returns the mainScreen
	 */
	public JPanel returnPanel() {
		return mainScreen;
	}
}
