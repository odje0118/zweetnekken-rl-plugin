package com.zweetnekken.ui;

import com.zweetnekken.model.ZweetnekkenModels;
import com.zweetnekken.service.ZweetnekkenDataService;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

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
	private boolean loading;
	private String message = "";
	private long requestGeneration;

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
		content.add(pvmDiaryCard());
		content.add(Box.createVerticalStrut(8));
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

	private JPanel pvmDiaryCard()
	{
		JPanel card = ZweetnekkenTheme.surface();
		card.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
		card.add(label("PvM Diary", true, ZweetnekkenTheme.TEXT));
		card.add(Box.createVerticalStrut(6));
		card.add(messageLabel("PvM Diary-profielweergave volgt later."));
		JButton diary = new JButton("View PvM Diary");
		diary.setEnabled(false);
		diary.setFont(ZweetnekkenTheme.font(false));
		diary.setForeground(MUTED);
		diary.setBackground(ZweetnekkenTheme.BACKGROUND);
		diary.setBorder(BorderFactory.createLineBorder(ZweetnekkenTheme.BORDER));
		diary.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		diary.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(Box.createVerticalStrut(7));
		card.add(diary);
		return card;
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

