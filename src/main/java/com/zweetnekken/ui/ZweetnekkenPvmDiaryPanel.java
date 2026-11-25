package com.zweetnekken.ui;

import com.zweetnekken.model.ZweetnekkenModels;
import com.zweetnekken.service.ZweetnekkenDataService;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
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
		ZweetnekkenModels.PvmDiaryStatus status = displayStatus();
		if (selectedActivity != null && status != null)
		{
			renderActivity(status, selectedActivity);
		}
		else if (selectedCategory != null && status != null)
		{
			renderCategory(status, selectedCategory);
		}
		else
		{
			if (data.pvmDiaryStatus() == null) renderUnavailableNotice();
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

	private void renderUnavailableNotice()
	{
		JPanel notice = card();
		notice.add(text("De doelen zijn beschikbaar. Je persoonlijke voortgang wordt geladen zodra de Diary API actief is.", false, MUTED));
		content.add(notice);
		content.add(Box.createVerticalStrut(6));
	}

	private ZweetnekkenModels.PvmDiaryStatus displayStatus()
	{
		ZweetnekkenModels.PvmDiaryStatus catalogue = PvmDiaryCatalogue.create();
		ZweetnekkenModels.PvmDiaryStatus live = data.pvmDiaryStatus();
		if (live == null) return catalogue;
		catalogue.fetchedAt = live.fetchedAt;
		catalogue.earnedPoints = live.earnedPoints;
		catalogue.completedTiers = live.completedTiers;
		catalogue.totalPoints = live.totalPoints > 0 ? live.totalPoints : catalogue.totalPoints;
		catalogue.totalTiers = live.totalTiers > 0 ? live.totalTiers : catalogue.totalTiers;
		catalogue.delivery = live.delivery;
		catalogue.pendingSubmissions = live.pendingSubmissions;
		for (ZweetnekkenModels.PvmDiaryCategory base : catalogue.categories)
			for (ZweetnekkenModels.PvmDiaryCategory remote : live.categories == null
				? java.util.Collections.<ZweetnekkenModels.PvmDiaryCategory>emptyList() : live.categories)
				if (base.name.equalsIgnoreCase(remote.name))
				{
					base.earnedPoints = remote.earnedPoints;
					base.totalPoints = remote.totalPoints > 0 ? remote.totalPoints : base.totalPoints;
					base.completedTiers = remote.completedTiers;
				}
		for (ZweetnekkenModels.PvmDiaryActivity base : catalogue.activities)
			for (ZweetnekkenModels.PvmDiaryActivity remote : live.activities == null
				? java.util.Collections.<ZweetnekkenModels.PvmDiaryActivity>emptyList() : live.activities)
				if (base.id.equalsIgnoreCase(remote.id))
				{
					base.earnedPoints = remote.earnedPoints;
					base.bestResult = remote.bestResult;
					base.nextTarget = remote.nextTarget;
					base.pendingCount = remote.pendingCount;
					for (ZweetnekkenModels.PvmDiaryTier tier : base.tiers)
						for (ZweetnekkenModels.PvmDiaryTier liveTier : remote.tiers == null
							? java.util.Collections.<ZweetnekkenModels.PvmDiaryTier>emptyList() : remote.tiers)
							if (tier.tier.equalsIgnoreCase(liveTier.tier)) tier.completed = liveTier.completed;
				}
		return catalogue;
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
		if (status.fetchedAt != null && !status.fetchedAt.isBlank())
			summary.add(text("Bijgewerkt: " + status.fetchedAt, false, MUTED));
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
			JButton open = actionButton(category.name, false);
			open.setFont(ZweetnekkenTheme.font(true));
			open.addActionListener(e -> { selectedCategory = category.name; selectedActivity = null; refresh(); });
			row.add(open);
			row.add(pair("tiers completed", category.completedTiers + " / " + category.totalTiers, GOLD));
			row.add(pair("PvM points", number(category.earnedPoints) + " / " + number(category.totalPoints) + " pts", ZweetnekkenTheme.BLUE));
			row.add(progress(category.earnedPoints, category.totalPoints, GOLD));
			content.add(row);
			content.add(ZweetnekkenPanel.blackDivider(4));
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
			String best = activity.bestResult == null || activity.bestResult.label == null ? "No record yet" : "Best: " + activity.bestResult.label;
			row.add(text(safe(activity.name), true, Color.WHITE));
			row.add(text(best + "  ·  " + number(activity.earnedPoints) + " points earned", false, MUTED));
			for (ZweetnekkenModels.PvmDiaryTier tier : activity.tiers)
			{
				JPanel tierRow = tierRow(tier);
				row.add(tierRow);
			}
			content.add(row);
			content.add(ZweetnekkenPanel.blackDivider(4));
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
			summary.add(pair("Beste resultaat", selected.bestResult.label, GREEN));
		if (selected.nextTarget != null && selected.nextTarget.targetLabel != null)
			summary.add(pair("Volgende doel", selected.nextTarget.targetLabel, Color.WHITE));
		content.add(summary);
		content.add(Box.createVerticalStrut(8));
		content.add(text("TIERS", true, GOLD));
		content.add(Box.createVerticalStrut(5));
		for (ZweetnekkenModels.PvmDiaryTier tier : selected.tiers == null
			? java.util.Collections.<ZweetnekkenModels.PvmDiaryTier>emptyList() : selected.tiers)
		{
			JPanel row = card();
			String target = tier.targetLabel == null || tier.targetLabel.isBlank()
				? (tier.target == null ? "Doel niet beschikbaar" : number(tier.target)) : tier.targetLabel;
			row.add(pair(safe(tier.tier), tier.completed ? "Voltooid" : target,
				tier.completed ? GREEN : MUTED));
			row.add(pair("Punten", number(tier.points), GOLD));
			content.add(row);
			content.add(ZweetnekkenPanel.blackDivider(4));
		}
	}

	private static JPanel tierRow(ZweetnekkenModels.PvmDiaryTier tier)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(true);
		row.setBackground(tier.completed ? new Color(31, 52, 35) : new Color(15, 16, 16));
		row.setBorder(BorderFactory.createEmptyBorder(7, 8, 7, 8));
		JPanel left = new JPanel();
		left.setOpaque(false);
		left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
		left.add(text(tier.tier, true, tier.completed ? GREEN : GOLD));
		left.add(text(tier.targetLabel == null ? "Completion" : tier.targetLabel, false, MUTED));
		row.add(left, BorderLayout.CENTER);
		row.add(text(tier.completed ? "Done · +" + tier.points : "+" + tier.points, true,
			tier.completed ? GREEN : GOLD), BorderLayout.EAST);
		return row;
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
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(text(name, false, MUTED), BorderLayout.WEST);
		row.add(text(value, false, valueColor), BorderLayout.EAST);
		return row;
	}

	private static JLabel text(String value, boolean bold, Color color)
	{
		JLabel label = new JLabel(value == null ? "—" : value);
		label.setToolTipText(value);
		label.setForeground(color);
		label.setFont(ZweetnekkenTheme.font(bold));
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
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

