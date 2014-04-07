package Gui;

import java.awt.Font;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

import net.miginfocom.swing.MigLayout;

public class Sample {

	public static void main(String[] args) {
		// JFrame
		JFrame frame = new JFrame();
		frame.setSize(250, 250);
		frame.setTitle("MichielChat");
		
		// JPanel
	    JPanel panel = new JPanel(new MigLayout());
	    
	    // add elements to panel
	    JTextField username = new JTextField("Enter username");
	    JTextField key = new JTextField("Enter key");
	    JButton enter = new JButton("Enter Michiel");
	    JLabel version = new JLabel("Version 1.0");
	    version.setFont(new Font("Arial", Font.PLAIN, 10));
	    version.setHorizontalAlignment( SwingConstants.CENTER ); 
	    JLabel title = new JLabel("MICHIEL CHAT");
	    title.setFont(new Font("Arial", Font.BOLD, 25));
	    title.setHorizontalAlignment( SwingConstants.CENTER ); 
	    JLabel subTitle = new JLabel("for your internet pleasure");
	    subTitle.setFont(new Font("Arial", Font.PLAIN, 10));
	    subTitle.setHorizontalAlignment( SwingConstants.CENTER ); 
	    
	    panel.add(title, "span, w 250px, h 10px");
	    panel.add(subTitle, "span, w 250px, h 30px");
	    panel.add(username, "span, w 250px, h 30px");
	    panel.add(key, "span, w 250px, h 30px");
	    panel.add(enter, "span, w 250px, h 50px");
	    panel.add(version, "span, w 250px, h 30px");
	    
	    frame.add(panel);
	    frame.setVisible(true);
	    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
	}

}
