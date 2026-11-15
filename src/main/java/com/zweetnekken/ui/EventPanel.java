package com.zweetnekken.ui;

import java.awt.Component;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import com.zweetnekken.model.ZweetnekkenModels;

/** Reusable collapsible card for displaying the current event supplied by an event source. */
final class EventPanel extends JPanel
{
	private final Supplier<List<ZweetnekkenModels.EventPanelEvent>> eventSupplier;
	private final JPanel content = ZweetnekkenPanel.verticalPanel();
	private final JButton toggle;
	private boolean expanded = true;

	EventPanel(Supplier<List<ZweetnekkenModels.EventPanelEvent>> eventSupplier)
	{
		this.eventSupplier = eventSupplier;
		setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(javax.swing.BorderFactory.createEmptyBorder(6, 8, 6, 8));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		toggle = ZweetnekkenPanel.sectionToggleButton("EVENT", true);
		toggle.addActionListener(e -> setExpanded(!expanded));
		add(toggle);
		content.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.setBorder(javax.swing.BorderFactory.createEmptyBorder(7, 0, 0, 0));
		add(content);
		refresh();
	}

	private void setExpanded(boolean value)
	{
		expanded = value;
		content.setVisible(value);
		toggle.setText((value ? "▾ " : "▸ ") + "EVENT");
		toggle.setToolTipText(value ? "Collapse EVENT" : "Expand EVENT");
		revalidate();
		repaint();
	}

	void refresh()
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(this::refresh);
			return;
		}
		content.removeAll();
		List<ZweetnekkenModels.EventPanelEvent> events;
		boolean unavailable = false;
		try
		{
			List<ZweetnekkenModels.EventPanelEvent> supplied = eventSupplier.get();
			events = supplied == null ? Collections.emptyList() : supplied;
		}
		catch (RuntimeException e)
		{
			events = Collections.emptyList();
			unavailable = true;
		}
		if (unavailable)
		{
			content.add(ZweetnekkenPanel.muted("Evenementgegevens tijdelijk niet beschikbaar"));
		}
		else if (events.isEmpty())
		{
			content.add(ZweetnekkenPanel.muted("Geen actief evenement"));
		}
		else
		{
			for (ZweetnekkenModels.EventPanelEvent event : events)
			{
				if (event == null) continue;
				content.add(ZweetnekkenPanel.sectionHeading(value(event.name, "Evenement")));
				List<ZweetnekkenModels.EventPanelEntry> entries = event.entries == null
					? Collections.emptyList() : event.entries;
				if (entries.isEmpty())
				{
					content.add(ZweetnekkenPanel.muted("Teamstanden zijn nog niet beschikbaar"));
					continue;
				}
				for (ZweetnekkenModels.EventPanelEntry entry : entries)
				{
					if (entry == null) continue;
					String label = value(entry.label, "Team");
					if (present(entry.value)) label += " — " + entry.value.trim();
					content.add(ZweetnekkenPanel.eventLeaderRow(entry.rank > 0 ? entry.rank : entries.indexOf(entry) + 1,
						value(entry.label, "Team"), value(entry.value, "—"), entry.progress));
					if (entries.indexOf(entry) < entries.size() - 1) content.add(Box.createVerticalStrut(3));
				}
			}
			if (content.getComponentCount() == 0)
				content.add(ZweetnekkenPanel.muted("Geen actief evenement"));
		}
		content.revalidate();
		content.repaint();
	}

	private static boolean present(String text) { return text != null && !text.trim().isEmpty(); }
	private static String value(String text, String fallback) { return present(text) ? text.trim() : fallback; }
}
