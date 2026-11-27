package com.zweetnekken.ui;

import com.zweetnekken.model.ZweetnekkenModels;
import com.zweetnekken.service.ZweetnekkenDataService;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.KeyStroke;

/** Public roster profile opened from a leaderboard member name. */
final class ZweetnekkenMemberProfilePanel extends JPanel
{
	private static final Color GOLD = ZweetnekkenTheme.GOLD;
	private static final Color MUTED = ZweetnekkenTheme.MUTED;
	private final ZweetnekkenDataService data;
	private final Consumer<String> backAction;
	private final JPanel content = new JPanel();
	private String memberName;
	private ZweetnekkenModels.Profile profile;
	private final List<ZweetnekkenModels.Profile> linkedProfiles = new ArrayList<>();
	private final Map<String, ZweetnekkenModels.PvmDiaryStatus> diariesByRsn = new HashMap<>();
	private final Set<String> pendingDiaryRsns = new HashSet<>();
	private final Set<String> unavailableDiaryRsns = new HashSet<>();
	private boolean loading;
	private String message = "";
	private String diaryViewRsn;
	private String diaryCategory;
	private long requestGeneration;
	private String diaryCatalogueVersion;

	ZweetnekkenMemberProfilePanel(ZweetnekkenDataService data, Consumer<String> backAction)
	{
		this.data = data;
		this.backAction = backAction;
		setLayout(new BorderLayout());
		setOpaque(false);
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setOpaque(false);
		content.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(content, BorderLayout.NORTH);
		render();
	}

	void load(String name)
	{
		memberName = name == null ? "" : name.trim();
		profile = null;
		linkedProfiles.clear();
		diariesByRsn.clear();
		pendingDiaryRsns.clear();
		unavailableDiaryRsns.clear();
		diaryViewRsn = null;
		diaryCategory = null;
		loading = true;
		message = "Profiel wordt geladen…";
		long generation = ++requestGeneration;
		render();
		data.lookupMemberProfile(memberName, result -> SwingUtilities.invokeLater(() ->
		{
			if (generation != requestGeneration) return;
			loading = false;
			if (result.isSuccessful() && result.value != null && result.value.member != null)
			{
				profile = result.value;
				message = "";
				loadDiaryStatus(generation, profile.member.name);
				loadLinkedProfiles(generation, profile);
			}
			else
			{
				profile = null;
				message = result.statusCode == 404
					? "Dit ledenprofiel is niet beschikbaar."
					: "Het ledenprofiel kon niet worden geladen.";
			}
			render();
		}));
	}

	private void render()
	{
		content.removeAll();
		if (diaryViewRsn != null)
		{
			renderDiaryPage();
			repaintContent();
			return;
		}
		JButton back = new JButton("‹  Leaderboards");
		back.setHorizontalAlignment(SwingConstants.LEFT);
		back.setForeground(ZweetnekkenTheme.TEXT);
		back.setBackground(ZweetnekkenTheme.BACKGROUND);
		back.setFont(ZweetnekkenTheme.font(true));
		back.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		back.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ZweetnekkenTheme.BORDER),
			BorderFactory.createEmptyBorder(7, 10, 7, 10)));
		back.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
		back.setAlignmentX(Component.LEFT_ALIGNMENT);
		back.addActionListener(e -> { if (backAction != null) backAction.accept(memberName); });
		content.add(back);
		content.add(Box.createVerticalStrut(10));

		if (loading || profile == null)
		{
			content.add(messageLabel(message));
			repaintContent();
			return;
		}

		content.add(memberCard());
		content.add(Box.createVerticalStrut(8));
		if (data.pvmDiaryConfig() != null)
		{
			content.add(pvmDiaryCard(profile.member.name, diaryStatus(profile.member.name)));
			content.add(Box.createVerticalStrut(8));
		}
		content.add(combatAchievementsCard(profile.combatAchievements));
		content.add(Box.createVerticalStrut(8));
		content.add(collectionLogCard(profile.collectionLog));
		for (ZweetnekkenModels.Profile linkedProfile : linkedProfiles)
		{
			if (linkedProfile == null || linkedProfile.member == null) continue;
			content.add(Box.createVerticalStrut(10));
			content.add(label(linkedProfile.member.name, true, ZweetnekkenTheme.TEXT));
			content.add(Box.createVerticalStrut(5));
			content.add(combatAchievementsCard(linkedProfile.combatAchievements));
			content.add(Box.createVerticalStrut(8));
			content.add(collectionLogCard(linkedProfile.collectionLog));
		}
		repaintContent();
	}

	private void loadLinkedProfiles(long generation, ZweetnekkenModels.Profile baseProfile)
	{
		if (baseProfile.member == null || baseProfile.member.linkedNames == null) return;
		Set<String> seen = new LinkedHashSet<>();
		seen.add(normalizeRsn(baseProfile.member.name));
		for (String linkedName : baseProfile.member.linkedNames)
		{
			if (linkedName == null || linkedName.isBlank() || !seen.add(normalizeRsn(linkedName))) continue;
			data.lookupMemberProfile(linkedName, result -> SwingUtilities.invokeLater(() ->
			{
				if (generation != requestGeneration || result == null || !result.isSuccessful()
					|| result.value == null || result.value.member == null) return;
				linkedProfiles.add(result.value);
				render();
			}));
		}
	}

	void refreshDiaryAccess()
	{
		if (profile == null || profile.member == null) return;
		ZweetnekkenModels.PvmDiaryConfig catalogue = data.pvmDiaryConfig();
		String version = catalogue == null ? null : catalogue.catalogueVersion;
		if (java.util.Objects.equals(diaryCatalogueVersion, version)) return;
		diaryCatalogueVersion = version;
		diaryViewRsn = null;
		diaryCategory = null;
		diariesByRsn.clear();
		unavailableDiaryRsns.clear();
		render();
		loadDiaryStatus(requestGeneration, profile.member.name);
	}

	private void loadDiaryStatus(long generation, String rsn)
	{
		ZweetnekkenModels.PvmDiaryConfig catalogue = data.pvmDiaryConfig();
		if (catalogue == null) return;
		String version = catalogue.catalogueVersion;
		diaryCatalogueVersion = version;
		String key = normalizeRsn(rsn);
		if (key.isBlank() || diariesByRsn.containsKey(key) || pendingDiaryRsns.contains(key)
			|| unavailableDiaryRsns.contains(key)) return;
		pendingDiaryRsns.add(key);
		data.lookupMemberPvmDiary(rsn, result -> SwingUtilities.invokeLater(() ->
		{
			if (generation != requestGeneration) return;
			pendingDiaryRsns.remove(key);
			if (!java.util.Objects.equals(version, diaryCatalogueVersion))
			{
				loadDiaryStatus(generation, rsn);
				return;
			}
			if (result != null && result.isSuccessful() && result.value != null)
				diariesByRsn.put(key, result.value);
			else
				unavailableDiaryRsns.add(key);
			render();
		}));
	}

	private ZweetnekkenModels.PvmDiaryStatus diaryStatus(String rsn)
	{
		ZweetnekkenModels.PvmDiaryStatus progress = diariesByRsn.get(normalizeRsn(rsn));
		return progress == null ? null : data.pvmDiaryView(progress);
	}

	private static String normalizeRsn(String name)
	{
		return name == null ? "" : name.trim().toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "").replace("-", "");
	}

	private JPanel memberCard()
	{
		ZweetnekkenModels.Standings standings = profile.standings;
		JPanel card = ZweetnekkenTheme.surface();
		card.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
		card.add(label(profile.member.name == null ? memberName : profile.member.name, true, ZweetnekkenTheme.TEXT));
		card.add(Box.createVerticalStrut(2));
		card.add(label(standings == null ? "—" : ZweetnekkenMemberSearchPanel.displayRank(
			standings.currentRank, standings.pvmPoints, standings.clanPoints), false, GOLD));
		card.add(Box.createVerticalStrut(10));
		if (standings == null)
		{
			card.add(messageLabel("Punten en posities zijn niet beschikbaar."));
			return card;
		}
		card.add(statRow("PvM Points", number(standings.pvmPoints), "PvM Position", position(standings.pvmRank), ZweetnekkenTheme.BLUE));
		card.add(Box.createVerticalStrut(7));
		card.add(statRow("Clan Points", number(standings.clanPoints), "Clan Position", position(standings.clanRank), ZweetnekkenTheme.GREEN));
		return card;
	}

	private JPanel combatAchievementsCard(ZweetnekkenModels.CombatAchievementProgress progress)
	{
		JPanel card = ZweetnekkenTheme.surface();
		card.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
		card.add(label("Combat Achievements", true, ZweetnekkenTheme.TEXT));
		card.add(Box.createVerticalStrut(7));
		if (progress == null)
		{
			card.add(messageLabel("Combat Achievement-gegevens zijn nog niet gepubliceerd voor dit lid."));
			return card;
		}
		card.add(statRow("Tier", value(progress.currentTier), "Tasks", progress.completedTasks + " / " + progress.totalTasks, GOLD));
		card.add(Box.createVerticalStrut(4));
		card.add(statRow("Points", number(progress.totalPoints), "", number(progress.totalPossiblePoints), ZweetnekkenTheme.BLUE));
		JProgressBar bar = new JProgressBar(0, 1000);
		bar.setValue(progress.totalPossiblePoints <= 0 ? 0 : (int) Math.min(1000,
			Math.max(0, progress.totalPoints * 1000L / progress.totalPossiblePoints)));
		bar.setBorderPainted(false);
		bar.setBackground(new Color(52, 55, 58));
		bar.setForeground(ZweetnekkenTheme.BLUE);
		bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 7));
		card.add(Box.createVerticalStrut(6));
		card.add(bar);
		String updatedAt = formatUpdatedAt(progress.updatedAt);
		if (!updatedAt.isEmpty())
		{
			card.add(Box.createVerticalStrut(6));
			card.add(messageLabel("Synced " + updatedAt));
		}
		return card;
	}

	private JPanel pvmDiaryCard(String rsn, ZweetnekkenModels.PvmDiaryStatus status)
	{
		JPanel card = ZweetnekkenTheme.surface();
		card.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
		card.add(label("PvM Diary", true, ZweetnekkenTheme.TEXT));
		card.add(Box.createVerticalStrut(6));
		String key = normalizeRsn(rsn);
		if (status == null)
		{
			card.add(messageLabel(pendingDiaryRsns.contains(key)
				? "PvM Diary wordt geladen…" : unavailableDiaryRsns.contains(key)
					? "PvM Diary kon niet worden geladen." : "PvM Diary is niet beschikbaar."));
			return card;
		}

		card.add(diaryStatRow("Points", number(status.earnedPoints) + " / " + number(status.totalPoints), GOLD));
		card.add(diaryStatRow("Tiers", status.completedTiers + " / " + status.totalTiers, ZweetnekkenTheme.TEXT));
		JProgressBar progress = new JProgressBar(0, 1000);
		progress.setValue(status.totalPoints <= 0 ? 0 : (int) Math.min(1000,
			Math.max(0, status.earnedPoints * 1000L / status.totalPoints)));
		progress.setBorderPainted(false);
		progress.setBackground(new Color(52, 55, 58));
		progress.setForeground(GOLD);
		progress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 7));
		card.add(Box.createVerticalStrut(7));
		card.add(progress);

		JButton diary = new JButton("Bekijk PvM Diary");
		diary.setFont(ZweetnekkenTheme.font(false));
		diary.setForeground(MUTED);
		diary.setBackground(ZweetnekkenTheme.BACKGROUND);
		diary.setFocusPainted(false);
		diary.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		diary.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(ZweetnekkenTheme.BORDER),
			BorderFactory.createEmptyBorder(5, 8, 5, 8)));
		diary.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
		diary.setAlignmentX(Component.LEFT_ALIGNMENT);
		diary.addActionListener(e ->
		{
			diaryViewRsn = rsn;
			diaryCategory = null;
			render();
		});
		card.add(Box.createVerticalStrut(8));
		card.add(diary);
		return card;
	}

	private static JPanel diaryStatRow(String name, String value, Color valueColor)
	{
		JPanel row = new JPanel(new GridBagLayout());
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel leftLabel = label(name, false, MUTED);
		GridBagConstraints left = new GridBagConstraints();
		left.gridx = 0;
		left.gridy = 0;
		left.weightx = 1;
		left.fill = GridBagConstraints.HORIZONTAL;
		left.anchor = GridBagConstraints.WEST;
		left.insets = new Insets(0, 0, 0, 6);
		row.add(leftLabel, left);
		JLabel rightLabel = label(value, false, valueColor, SwingConstants.RIGHT);
		GridBagConstraints right = new GridBagConstraints();
		right.gridx = 1;
		right.gridy = 0;
		right.anchor = GridBagConstraints.EAST;
		row.add(rightLabel, right);
		int height = row.getPreferredSize().height;
		row.setMinimumSize(new Dimension(0, height));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		return row;
	}

	private void renderDiaryPage()
	{
		JButton leaderboardBack = diaryBackButton("‹  Leaderboards");
		leaderboardBack.addActionListener(e -> { if (backAction != null) backAction.accept(memberName); });
		content.add(leaderboardBack);
		content.add(Box.createVerticalStrut(6));
		JButton profileBack = diaryBackButton("‹  Profiel");
		profileBack.addActionListener(e -> { diaryViewRsn = null; diaryCategory = null; render(); });
		content.add(profileBack);
		content.add(Box.createVerticalStrut(8));
		String rsn = diaryViewRsn;
		content.add(label(profileDisplayName(), true, ZweetnekkenTheme.TEXT));
		content.add(Box.createVerticalStrut(8));
		ZweetnekkenModels.PvmDiaryStatus status = diaryStatus(rsn);
		if (status == null)
		{
			String key = normalizeRsn(rsn);
			content.add(messageLabel(pendingDiaryRsns.contains(key)
				? "PvM Diary wordt geladen…" : "PvM Diary kon niet worden geladen."));
			return;
		}
		if (diaryCategory == null) renderDiaryCategories(status);
		else renderDiaryCategory(status, diaryCategory);
	}

	private String profileDisplayName()
	{
		LinkedHashSet<String> names = new LinkedHashSet<>();
		if (profile != null && profile.member != null && profile.member.name != null)
			names.add(profile.member.name);
		for (ZweetnekkenModels.Profile linked : linkedProfiles)
			if (linked != null && linked.member != null && linked.member.name != null)
				names.add(linked.member.name);
		return String.join(" / ", names);
	}

	private void renderDiaryCategories(ZweetnekkenModels.PvmDiaryStatus status)
	{
		for (ZweetnekkenModels.PvmDiaryCategory category : status.categories == null
			? java.util.Collections.<ZweetnekkenModels.PvmDiaryCategory>emptyList() : status.categories)
		{
			JPanel row = ZweetnekkenTheme.surface();
			row.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 3, 0, 0, ZweetnekkenTheme.BLUE),
				BorderFactory.createEmptyBorder(9, 10, 9, 10)));
			JPanel heading = new JPanel(new BorderLayout(5, 0));
			heading.setOpaque(false);
			heading.add(label(category.name, true, ZweetnekkenTheme.TEXT), BorderLayout.WEST);
			heading.add(label(category.completedTiers + "/" + category.totalTiers, true, GOLD, SwingConstants.RIGHT), BorderLayout.EAST);
			row.add(heading);
			row.add(Box.createVerticalStrut(5));
			row.add(diaryStatRow("Tiers voltooid",
				category.completedTiers + " / " + category.totalTiers, MUTED));
			row.add(diaryStatRow("PvM points",
				number(category.earnedPoints) + " / " + number(category.totalPoints), ZweetnekkenTheme.BLUE));
			row.add(Box.createVerticalStrut(5));
			row.add(diaryProgress(category.earnedPoints, category.totalPoints));
			makeDiaryClickable(row, () -> { diaryCategory = category.name; render(); });
			content.add(row);
			content.add(Box.createVerticalStrut(8));
		}
	}

	private void renderDiaryCategory(ZweetnekkenModels.PvmDiaryStatus status, String categoryName)
	{
		JButton back = diaryBackButton("‹  Alle Diary's");
		back.addActionListener(e -> { diaryCategory = null; render(); });
		content.add(back);
		content.add(Box.createVerticalStrut(8));
		JLabel title = label(categoryName.toUpperCase(Locale.ROOT), true, GOLD);
		content.add(title);
		content.add(Box.createVerticalStrut(8));
		ZweetnekkenModels.PvmDiaryCategory category = null;
		for (ZweetnekkenModels.PvmDiaryCategory candidate : status.categories == null
			? java.util.Collections.<ZweetnekkenModels.PvmDiaryCategory>emptyList() : status.categories)
			if (candidate.name != null && candidate.name.equalsIgnoreCase(categoryName)) { category = candidate; break; }
		if (category != null)
		{
			JPanel summary = ZweetnekkenTheme.surface();
			summary.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
			summary.add(label(category.completedTiers + " / " + category.totalTiers + " tiers voltooid", true, ZweetnekkenTheme.TEXT));
			summary.add(Box.createVerticalStrut(3));
			summary.add(label(number(category.earnedPoints) + " / " + number(category.totalPoints) + " PvM points", false, MUTED));
			summary.add(Box.createVerticalStrut(7));
			summary.add(diaryProgress(category.earnedPoints, category.totalPoints));
			content.add(summary);
			content.add(Box.createVerticalStrut(8));
		}
		boolean found = false;
		for (ZweetnekkenModels.PvmDiaryActivity activity : status.activities == null
			? java.util.Collections.<ZweetnekkenModels.PvmDiaryActivity>emptyList() : status.activities)
		{
			if (activity == null || activity.category == null || !activity.category.equalsIgnoreCase(categoryName)) continue;
			found = true;
			JPanel panel = ZweetnekkenTheme.surface();
			panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
			panel.add(label(value(activity.name), true, ZweetnekkenTheme.TEXT));
			panel.add(Box.createVerticalStrut(4));
			String recordLabel = ZweetnekkenPvmDiaryPanel.recordLabel(activity.kind);
			if (recordLabel != null)
				panel.add(diaryStatRow(recordLabel, ZweetnekkenPvmDiaryPanel.recordValue(activity), MUTED));
			panel.add(diaryStatRow("Punten verdiend", number(activity.earnedPoints), MUTED));
			panel.add(Box.createVerticalStrut(7));
			for (ZweetnekkenModels.PvmDiaryTier tier : activity.tiers == null
				? java.util.Collections.<ZweetnekkenModels.PvmDiaryTier>emptyList() : activity.tiers)
			{
				panel.add(diaryTierRow(tier));
				panel.add(Box.createVerticalStrut(5));
			}
			content.add(panel);
			content.add(Box.createVerticalStrut(8));
		}
		if (!found) content.add(messageLabel("There are no activities in this category yet."));
	}

	private static JButton diaryBackButton(String text)
	{
		JButton button = new JButton(text);
		button.setHorizontalAlignment(SwingConstants.LEFT);
		button.setForeground(ZweetnekkenTheme.TEXT);
		button.setBackground(ZweetnekkenTheme.BACKGROUND);
		button.setFont(ZweetnekkenTheme.font(true));
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(ZweetnekkenTheme.BORDER),
			BorderFactory.createEmptyBorder(7, 10, 7, 10)));
		button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
		button.setAlignmentX(Component.LEFT_ALIGNMENT);
		return button;
	}

	private static JProgressBar diaryProgress(long current, long total)
	{
		JProgressBar bar = new JProgressBar(0, 1000);
		bar.setValue(total <= 0 ? 0 : (int) Math.min(1000, Math.max(0, current * 1000L / total)));
		bar.setBorderPainted(false);
		bar.setBackground(new Color(52, 55, 58));
		bar.setForeground(GOLD);
		bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 7));
		return bar;
	}

	private static JPanel diaryTierRow(ZweetnekkenModels.PvmDiaryTier tier)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(true);
		row.setBackground(tier.completed ? new Color(31, 52, 35) : ZweetnekkenTheme.BACKGROUND);
		row.setBorder(BorderFactory.createEmptyBorder(8, 9, 8, 9));
		JPanel left = new JPanel();
		left.setOpaque(false);
		left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
		left.add(label(tier.tier, true, tier.completed ? ZweetnekkenTheme.GREEN : GOLD));
		left.add(label(tier.targetLabel == null || tier.targetLabel.isBlank()
			? "Completion" : "completion".equalsIgnoreCase(tier.targetLabel) ? "Completion" : tier.targetLabel,
			false, MUTED));
		row.add(left, BorderLayout.CENTER);
		JPanel right = new JPanel();
		right.setOpaque(false);
		right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
		if (tier.completed) right.add(label("Voltooid", true, ZweetnekkenTheme.GREEN));
		right.add(label("+" + number(tier.points), true, tier.completed ? ZweetnekkenTheme.GREEN : GOLD));
		row.add(right, BorderLayout.EAST);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	private static void makeDiaryClickable(JPanel panel, Runnable action)
	{
		panel.setFocusable(true);
		panel.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "openDiaryCategory");
		panel.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("SPACE"), "openDiaryCategory");
		panel.getActionMap().put("openDiaryCategory", new AbstractAction()
		{
			@Override public void actionPerformed(java.awt.event.ActionEvent event) { action.run(); }
		});
		makeDiaryClickableRecursive(panel, action);
	}

	private static void makeDiaryClickableRecursive(Component component, Runnable action)
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
			for (Component child : ((Container) component).getComponents()) makeDiaryClickableRecursive(child, action);
	}

	private JPanel collectionLogCard(ZweetnekkenModels.CollectionLogProgress progress)
	{
		JPanel card = ZweetnekkenTheme.surface();
		card.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
		card.add(label("Collection Log", true, ZweetnekkenTheme.TEXT));
		card.add(Box.createVerticalStrut(7));
		if (progress == null)
		{
			card.add(messageLabel("Collection Log is nog niet gesynchroniseerd."));
			return card;
		}
		card.add(statRow("Slots obtained", number(progress.obtainedCount), "", number(progress.totalCount), GOLD));
		JProgressBar bar = new JProgressBar(0, 1000);
		bar.setValue(progress.totalCount <= 0 ? 0 : (int) Math.min(1000,
			Math.max(0, progress.obtainedCount * 1000L / progress.totalCount)));
		bar.setBorderPainted(false);
		bar.setBackground(new Color(52, 55, 58));
		bar.setForeground(GOLD);
		bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 7));
		card.add(Box.createVerticalStrut(6));
		card.add(bar);
		String updatedAt = formatUpdatedAt(progress.updatedAt);
		if (!updatedAt.isEmpty())
		{
			card.add(Box.createVerticalStrut(6));
			card.add(messageLabel("Synced " + updatedAt));
		}
		return card;
	}

	private static JPanel statRow(String leftLabel, String leftValue, String rightLabel, String rightValue, Color valueColor)
	{
		JPanel row = new JPanel(new BorderLayout(5, 0));
		row.setOpaque(false);
		JPanel left = new JPanel();
		left.setOpaque(false);
		left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
		left.add(label(leftLabel, false, MUTED));
		left.add(label(leftValue, true, valueColor));
		JPanel right = new JPanel();
		right.setOpaque(false);
		right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
		right.setAlignmentX(Component.RIGHT_ALIGNMENT);
		right.add(label(rightLabel, false, MUTED, SwingConstants.RIGHT));
		right.add(label(rightValue, true, valueColor, SwingConstants.RIGHT));
		row.add(left, BorderLayout.WEST);
		row.add(right, BorderLayout.EAST);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		return row;
	}

	private static JLabel label(String text, boolean bold, Color color)
	{
		return label(text, bold, color, SwingConstants.LEFT);
	}

	private static JLabel label(String text, boolean bold, Color color, int alignment)
	{
		JLabel label = new JLabel(text, alignment);
		label.setForeground(color);
		label.setFont(ZweetnekkenTheme.font(bold));
		return label;
	}

	private static JLabel messageLabel(String text)
	{
		JLabel label = label(text, false, MUTED);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private static String number(long value)
	{
		return String.format(Locale.US, "%,d", value);
	}

	private static String position(Integer value)
	{
		return value == null || value <= 0 ? "—" : "#" + value;
	}

	private static String value(String value)
	{
		return value == null || value.isBlank() ? "—" : value;
	}

	private static String formatUpdatedAt(String value)
	{
		if (value == null || value.isBlank()) return "";
		try
		{
			ZonedDateTime timestamp;
			try
			{
				timestamp = OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault());
			}
			catch (RuntimeException ignored)
			{
				// Also accept timestamps without an explicit offset from older API responses.
				timestamp = LocalDateTime.parse(value).atZone(ZoneId.systemDefault());
			}
			return DateTimeFormatter.ofPattern("MMM d, HH:mm z", Locale.ENGLISH).format(timestamp);
		}
		catch (RuntimeException e)
		{
			return "";
		}
	}

	private void repaintContent()
	{
		ZweetnekkenTheme.typography(content);
		content.revalidate();
		content.repaint();
		revalidate();
		repaint();
	}
}
