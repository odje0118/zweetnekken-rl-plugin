package com.zweetnekken.ui;

import com.zweetnekken.model.ZweetnekkenModels;
import com.zweetnekken.service.ZweetnekkenDataService;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;

/** PvM Diary summary and drill-down view for the logged-in clan member. */
final class ZweetnekkenPvmDiaryPanel extends JPanel
{
	private static final Color GOLD = ZweetnekkenTheme.GOLD;
	private static final Color GREEN = ZweetnekkenTheme.GREEN;
	private static final Color MUTED = ZweetnekkenTheme.MUTED;
	private final ZweetnekkenDataService data;
	private final JPanel content = new JPanel();
	private String selectedCategory;
	private String selectedActivity;

	ZweetnekkenPvmDiaryPanel(ZweetnekkenDataService data)
	{
		this.data = data;
		setLayout(new BorderLayout());
		setOpaque(false);
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setOpaque(false);
		add(content, BorderLayout.NORTH);
		refresh();
	}

	void refresh()
	{
		content.removeAll();
		ZweetnekkenModels.PvmDiaryStatus status = data.pvmDiaryView();
		if (status == null)
		{
			selectedActivity = null;
			selectedCategory = null;
			renderUnavailable();
		}
		else if (selectedActivity != null && status.activities.stream().anyMatch(a -> selectedActivity.equals(a.id)))
		{
			renderActivity(status, selectedActivity);
		}
		else if (selectedCategory != null && status.categories.stream().anyMatch(c -> selectedCategory.equals(c.name)))
		{
			selectedActivity = null;
			renderCategory(status, selectedCategory);
		}
		else
		{
			selectedActivity = null;
			selectedCategory = null;
			if (data.pvmDiaryStatus() == null) content.add(text(data.pvmDiaryMessage(), false, MUTED));
			renderOverview(status);
		}
		revalidate();
		repaint();
		ZweetnekkenTheme.typography(content);
	}

	private void renderUnavailable()
	{
		content.add(heading("PvM Diary"));
		content.add(Box.createVerticalStrut(8));
		JPanel card = card();
		card.add(text(data.pvmDiaryMessage(), false, MUTED));
		card.add(Box.createVerticalStrut(8));
		JButton retry = actionButton("Vernieuw PvM Diary", true);
		retry.addActionListener(e -> {
			ZweetnekkenModels.Profile profile = data.profile();
			String name = profile == null || profile.member == null ? null : profile.member.name;
			data.refreshPvmDiary(name);
		});
		card.add(retry);
		content.add(card);
	}

	private void renderOverview(ZweetnekkenModels.PvmDiaryStatus status)
	{
		content.add(heading("PvM Diary"));
		content.add(Box.createVerticalStrut(8));
		JPanel summary = card();
		summary.add(text("Voortgang", true, Color.WHITE));
		summary.add(Box.createVerticalStrut(8));
		summary.add(pair("Punten", number(status.earnedPoints) + " / " + number(status.totalPoints), GOLD));
		summary.add(pair("Voltooide tiers", status.completedTiers + " / " + status.totalTiers, GREEN));
		JProgressBar progress = progress(status.earnedPoints, status.totalPoints, GOLD);
		summary.add(Box.createVerticalStrut(8));
		summary.add(progress);
		content.add(summary);
		content.add(Box.createVerticalStrut(8));
		content.add(text("CATEGORIEËN", true, GOLD));
		content.add(Box.createVerticalStrut(5));
		List<ZweetnekkenModels.PvmDiaryCategory> categories = status.categories == null
			? new ArrayList<>() : new ArrayList<>(status.categories);
		if (categories.isEmpty()) content.add(cardWithMessage("Er zijn nog geen PvM Diary-categorieën beschikbaar."));
		for (ZweetnekkenModels.PvmDiaryCategory category : categories)
		{
			JPanel row = card();
			row.setToolTipText("Bekijk " + category.name);
			JPanel titleRow = new JPanel(new BorderLayout(6, 0));
			titleRow.setOpaque(false);
			titleRow.add(text(category.name, true, ZweetnekkenTheme.TEXT), BorderLayout.CENTER);
			titleRow.add(text("›", true, GOLD), BorderLayout.EAST);
			titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, titleRow.getPreferredSize().height));
			row.add(titleRow);
			row.add(Box.createVerticalStrut(7));
			row.add(pair("Tiers behaald", category.completedTiers + " / " + category.totalTiers, GOLD));
			row.add(pair("PvM-punten", number(category.earnedPoints) + " / " + number(category.totalPoints), ZweetnekkenTheme.BLUE));
			row.add(Box.createVerticalStrut(6));
			row.add(progress(category.earnedPoints, category.totalPoints, GOLD));
			makeClickable(row, () -> { selectedCategory = category.name; selectedActivity = null; refresh(); });
			content.add(row);
			content.add(ZweetnekkenPanel.blackDivider(6));
		}
	}

	private void renderCategory(ZweetnekkenModels.PvmDiaryStatus status, String categoryName)
	{
		addBackButton("‹  Alle categorieën", () -> { selectedCategory = null; selectedActivity = null; refresh(); });
		content.add(Box.createVerticalStrut(8));
		content.add(heading(categoryName.toUpperCase(Locale.ROOT)));
		content.add(Box.createVerticalStrut(8));
		List<ZweetnekkenModels.PvmDiaryActivity> activities = new ArrayList<>();
		for (ZweetnekkenModels.PvmDiaryActivity activity : status.activities == null
			? java.util.Collections.<ZweetnekkenModels.PvmDiaryActivity>emptyList() : status.activities)
			if (activity != null && categoryName.equalsIgnoreCase(safe(activity.category))) activities.add(activity);
		if (activities.isEmpty()) content.add(cardWithMessage("Er zijn geen activiteiten in deze categorie."));
		for (ZweetnekkenModels.PvmDiaryActivity activity : activities)
		{
			JPanel row = card();
			JPanel activityHeading = new JPanel(new BorderLayout());
			activityHeading.setOpaque(false);
			activityHeading.setAlignmentX(Component.LEFT_ALIGNMENT);
			activityHeading.add(text(safe(activity.name), true, ZweetnekkenTheme.TEXT), BorderLayout.WEST);
			activityHeading.setMaximumSize(new Dimension(Integer.MAX_VALUE, activityHeading.getPreferredSize().height));
			row.add(activityHeading);
			row.add(Box.createVerticalStrut(3));
			row.add(pair("time".equals(activity.kind) ? "Beste tijd" : "Beste resultaat",
				activity.bestResult == null || activity.bestResult.label == null
				? "Nog geen record" : activity.bestResult.label, GREEN));
			row.add(pair("Punten behaald", number(activity.earnedPoints), ZweetnekkenTheme.BLUE));
			row.add(Box.createVerticalStrut(7));
			for (ZweetnekkenModels.PvmDiaryTier tier : activity.tiers)
			{
				JPanel tierRow = tierRow(tier);
				row.add(tierRow);
				row.add(Box.createVerticalStrut(5));
			}
			content.add(row);
			content.add(ZweetnekkenPanel.blackDivider(6));
		}
	}

	private void renderActivity(ZweetnekkenModels.PvmDiaryStatus status, String activityId)
	{
		ZweetnekkenModels.PvmDiaryActivity selected = null;
		for (ZweetnekkenModels.PvmDiaryActivity activity : status.activities == null
			? java.util.Collections.<ZweetnekkenModels.PvmDiaryActivity>emptyList() : status.activities)
			if (activity != null && activityId.equals(activity.id)) { selected = activity; break; }
		if (selected == null)
		{
			selectedActivity = null;
			refresh();
			return;
		}
		addBackButton("‹  " + safe(selected.category), () -> { selectedActivity = null; refresh(); });
		content.add(Box.createVerticalStrut(8));
		content.add(heading(safe(selected.name).toUpperCase(Locale.ROOT)));
		content.add(Box.createVerticalStrut(8));
		JPanel summary = card();
		summary.add(pair("Teamgrootte", selected.teamSize > 0 ? String.valueOf(selected.teamSize) : "—", Color.WHITE));
		summary.add(pair("Punten", number(selected.earnedPoints), GOLD));
		if (selected.bestResult != null && selected.bestResult.label != null)
			summary.add(pair("time".equals(selected.kind) ? "Beste tijd" : "Beste resultaat",
				selected.bestResult.label, GREEN));
		if (selected.nextTarget != null && selected.nextTarget.targetLabel != null)
			summary.add(pair("Volgende doel", selected.nextTarget.targetLabel, Color.WHITE));
		content.add(summary);
		content.add(Box.createVerticalStrut(8));
		content.add(text("TIERS", true, GOLD));
		content.add(Box.createVerticalStrut(5));
		for (ZweetnekkenModels.PvmDiaryTier tier : selected.tiers == null
			? java.util.Collections.<ZweetnekkenModels.PvmDiaryTier>emptyList() : selected.tiers)
		{
			content.add(tierRow(tier));
			content.add(ZweetnekkenPanel.blackDivider(4));
		}
	}

	private static JPanel tierRow(ZweetnekkenModels.PvmDiaryTier tier)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(true);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setBackground(tier.completed ? new Color(31, 52, 35) : ZweetnekkenTheme.BACKGROUND);
		row.setBorder(BorderFactory.createEmptyBorder(8, 9, 8, 9));
		JPanel left = new JPanel();
		left.setOpaque(false);
		left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
		left.add(text(safe(tier.tier), true, tier.completed ? GREEN : GOLD));
		left.add(text(localizedTarget(tier.targetLabel), false, MUTED));
		row.add(left, BorderLayout.CENTER);
		JPanel reward = new JPanel();
		reward.setOpaque(false);
		reward.setLayout(new BoxLayout(reward, BoxLayout.Y_AXIS));
		if (tier.completed) reward.add(text("VOLTOOID", true, GREEN));
		reward.add(text("+" + number(tier.points) + " punten", true, tier.completed ? GREEN : GOLD));
		row.add(reward, BorderLayout.EAST);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	private static String localizedTarget(String value)
	{
		if (value == null || value.isBlank() || "completion".equalsIgnoreCase(value)) return "Completion";
		return value;
	}

	private void addBackButton(String label, Runnable action)
	{
		JButton back = actionButton(label, false);
		back.addActionListener(e -> action.run());
		content.add(back);
	}

	private static JPanel cardWithMessage(String message)
	{
		JPanel card = card();
		card.add(text(message, false, MUTED));
		return card;
	}

	private static JPanel card()
	{
		return ZweetnekkenPanel.card();
	}

	private static void makeClickable(JPanel panel, Runnable action)
	{
		panel.setFocusable(true);
		panel.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "openDiaryCategory");
		panel.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("SPACE"), "openDiaryCategory");
		panel.getActionMap().put("openDiaryCategory", new AbstractAction()
		{
			@Override public void actionPerformed(java.awt.event.ActionEvent event) { action.run(); }
		});
		addMouseClick(panel, action);
	}

	private static void addMouseClick(Component component, Runnable action)
	{
		component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		component.addMouseListener(new MouseAdapter()
		{
			@Override public void mouseClicked(MouseEvent event)
			{
				if (event.getButton() == MouseEvent.BUTTON1) action.run();
			}
		});
		if (component instanceof Container)
			for (Component child : ((Container) component).getComponents()) addMouseClick(child, action);
	}

	private static JPanel heading(String value)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel label = text(value, true, GOLD);
		label.setFont(ZweetnekkenTheme.font(true));
		row.add(label, BorderLayout.WEST);
		return row;
	}

	private static JPanel pair(String name, String value, Color valueColor)
	{
		JPanel row = new JPanel(new GridBagLayout());
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel nameLabel = text(name, false, MUTED);
		JLabel valueLabel = text(value, false, valueColor);
		valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		GridBagConstraints left = new GridBagConstraints();
		left.gridx = 0;
		left.gridy = 0;
		left.weightx = 1;
		left.fill = GridBagConstraints.HORIZONTAL;
		left.anchor = GridBagConstraints.WEST;
		left.insets = new Insets(0, 0, 0, 5);
		row.add(nameLabel, left);
		GridBagConstraints right = new GridBagConstraints();
		right.gridx = 1;
		right.gridy = 0;
		right.weightx = 0;
		right.anchor = GridBagConstraints.EAST;
		right.insets = new Insets(0, 0, 0, 0);
		row.add(valueLabel, right);
		int height = row.getPreferredSize().height;
		row.setMinimumSize(new Dimension(0, height));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		return row;
	}

	private static JLabel text(String value, boolean bold, Color color)
	{
		JLabel label = new JLabel(value == null ? "—" : value);
		label.setToolTipText(value);
		label.setForeground(color);
		label.setFont(ZweetnekkenTheme.font(bold));
		label.setHorizontalAlignment(SwingConstants.LEFT);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setMaximumSize(new Dimension(Integer.MAX_VALUE, label.getPreferredSize().height));
		return label;
	}

	private static JButton actionButton(String title, boolean primary)
	{
		JButton button = ZweetnekkenPanel.actionButton(title, primary);
		button.setHorizontalAlignment(SwingConstants.LEFT);
		button.setAlignmentX(Component.LEFT_ALIGNMENT);
		button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		button.setPreferredSize(new Dimension(0, 28));
		return button;
	}

	private static JProgressBar progress(long current, long total, Color color)
	{
		JProgressBar progress = new JProgressBar(0, 1000);
		progress.setValue(total <= 0 ? 0 : (int) Math.min(1000, Math.max(0, Math.round(1000d * current / total))));
		progress.setStringPainted(false);
		progress.setBorderPainted(false);
		progress.setBackground(new Color(52, 55, 58));
		progress.setForeground(color);
		progress.setPreferredSize(new Dimension(100, 8));
		progress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
		return progress;
	}

	private static String number(long number)
	{
		return java.text.NumberFormat.getIntegerInstance(Locale.US).format(number);
	}

	private static String safe(String value) { return value == null || value.isBlank() ? "Onbekend" : value; }
}
