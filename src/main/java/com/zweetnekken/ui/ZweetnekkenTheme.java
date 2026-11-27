package com.zweetnekken.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.LayoutManager;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import net.runelite.client.ui.FontManager;

/** Shared palette and typography copied from the installed The Anchor panel theme. */
final class ZweetnekkenTheme
{
	static final Color BACKGROUND = new Color(15, 17, 17);
	static final Color SURFACE = new Color(28, 31, 33);
	static final Color BORDER = new Color(50, 54, 58);
	static final Color TEXT = new Color(230, 230, 220);
	static final Color MUTED = new Color(150, 150, 140);
	static final Color GOLD = new Color(218, 165, 32);
	static final Color BLUE = new Color(66, 165, 245);
	static final Color GREEN = new Color(139, 195, 74);
	static final Color HOVER = new Color(35, 38, 42);

	private ZweetnekkenTheme() { }

	static Font font(boolean bold)
	{
		return bold ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont();
	}

	static void typography(Component component)
	{
		Font current = component.getFont();
		if (current != null
				&& !current.getName().equals(FontManager.getRunescapeBoldFont().getName())
				&& !current.getName().equals(FontManager.getRunescapeSmallFont().getName()))
		{
			component.setFont(font(current.isBold()));
		}
		if (component instanceof Container)
			for (Component child : ((Container) component).getComponents()) typography(child);
	}

	static JPanel surface()
	{
		JPanel panel = surface(new java.awt.FlowLayout());
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		return panel;
	}

	static JPanel surface(LayoutManager layout)
	{
		JPanel panel = new JPanel(layout)
		{
			@Override protected void paintComponent(java.awt.Graphics graphics)
			{
				java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
				g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
						java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
				Object fill = getClientProperty("fill");
				g.setColor(fill instanceof Color ? (Color) fill : SURFACE);
				g.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
				g.dispose();
			}

			@Override public java.awt.Dimension getMaximumSize()
			{
				return new java.awt.Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		panel.setOpaque(false);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 12, 10, 12));
		return panel;
	}
}
