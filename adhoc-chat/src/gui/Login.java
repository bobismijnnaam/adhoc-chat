package gui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import util.GradientPanel;
import util.RoundJTextfield;
import net.miginfocom.swing.MigLayout;

public class Login {
	
	// username final for actionlistener acces
	final private RoundJTextfield username = new RoundJTextfield(5);
	// icons
	final private Icon enterImage = new ImageIcon("images/button.png");
    final private Icon enterImageHover = new ImageIcon("images/buttonHover.png");
    private Icon logo = new ImageIcon("images/logo.png");
    final private JButton enter = new JButton(enterImage);
    private JLabel version = new JLabel("Version 1.0");
	private JButton title = new JButton(logo);
	private GradientPanel panel;
	
	public Login() {
		// start with creating a new panel, a gradientpanel with mig
	    panel = new GradientPanel(new MigLayout());
	    
	    // initialize all the elements
	    initialize();
	    
	    // add action listerners
	    addListeners();
	    
	    // add everything to the panel
	    panel.add(title, "span, w 350px, h 70px");
	    panel.add(username, "wrap 10px, w 350px, h 40px");
	    panel.add(enter, "span, w 350px, h 55px");
	    panel.add(version, "span, w 350px, h 30px");
	}
	
	public void addController(GuiHandler handler){
        enter.addActionListener(handler);
	} //addController()
	
	/*
	 * adds action listeners to the elements that need them.
	 */
	private void addListeners() {
		// remove text on click
		username.addMouseListener(new MouseAdapter() {
	    	boolean typed = false;
	    	@Override
	    	public void mouseClicked(MouseEvent e) {
	    		username.setForeground(Color.BLACK);
	    		if (!typed) {
	    			username.setText("");
	    			username.setFont(new Font("Arial", Font.PLAIN, 12));
	    			typed = true;
	    		}
	    	}
    	});
		
		// change button on click
		enter.addMouseListener(new MouseAdapter() {
	    	@Override
	    	public void mouseEntered(MouseEvent e) {
	    		enter.setIcon(enterImageHover);
	    	}
	    	
	    	@Override
	    	public void mouseExited(MouseEvent e) {
	    		enter.setIcon(enterImage);
	    	}
    	});
	}
	
	/* 
	 *  Initializes all the elements, gives them the right properties.
	 */
	private void initialize() {
		// username
		username.setText("Enter username");
	    username.setBorder(BorderFactory.createCompoundBorder(
	            username.getBorder(), 
	            BorderFactory.createEmptyBorder(5, 10, 5, 5)));
	    
	    // enter button
	    enter.setMargin(new Insets(0, 0, 0, 0));
		enter.setOpaque(false);
		enter.setContentAreaFilled(false);
		enter.setBorderPainted(false);
	    enter.setBorder(null);
	    enter.setName("login");
	    
	    // version
	    version.setForeground(Color.DARK_GRAY);
	    version.setFont(new Font("Arial", Font.PLAIN, 10));
	    version.setHorizontalAlignment( SwingConstants.CENTER ); 
	    
	    // title
	    title.setMargin(new Insets(0, 0, 0, 0));
	    title.setOpaque(false);
	    title.setContentAreaFilled(false);
	    title.setBorderPainted(false);
	    title.setBorder(null);
	    
	    title.setFont(new Font("Arial", Font.BOLD, 25));
	    title.setHorizontalAlignment( SwingConstants.CENTER ); 
	}
	
	/*
	 * Returns the current picked username of the user
	 */
	public String getUsername() {
		return username.getText();
	}
	
	/*
	 * Sets the color of the username to red
	 */
	public void setUsernameBad() {
		username.setForeground(Color.RED);
	}
	
	/*
	 * Returns the created panel
	 */
	public JPanel createPanel() {
		return panel;
	}
}
