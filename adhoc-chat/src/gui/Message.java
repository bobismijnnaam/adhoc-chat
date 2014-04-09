package gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;

import javax.swing.JButton;
import javax.swing.SwingConstants;

public class Message extends JButton {
	
	Color color1, color2;  

	public Message(String text, Color color1, Color color2)  
    {  
        super(text);  
        this.color1 = color1;  
        this.color2 = color2;  
        setOpaque(false);  
		setContentAreaFilled(false);
		setBorderPainted(false);
		setFocusPainted(false);
        setHorizontalAlignment(SwingConstants.LEFT);
    }  
   
    public void paintComponent(Graphics g)  
    {  
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        int w = getWidth();
        int h = getHeight();
        GradientPaint gp = new GradientPaint(0, 0, color1, 0, h, color2);
        g2d.setPaint(gp);
        g2d.fillRect(0, 0, w, h);
        Graphics2D g2 = (Graphics2D)g;  
    	super.paintComponent(g);
    }  
}
