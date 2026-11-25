package com.zweetnekken.ui;

import com.zweetnekken.api.ZweetnekkenApiClient;
import com.zweetnekken.model.ZweetnekkenModels;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.DefaultListCellRenderer;
import javax.swing.SwingConstants;
import javax.swing.BorderFactory;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.SwingUtilities;
import java.util.function.Consumer;

/** Clan-wide points and rank leaderboard. */
final class ZweetnekkenMemberSearchPanel extends JPanel
{
	enum SortMode
	{
		PVM("PvM Points"), CLAN("Clan Points"), RANK("Clan Rank");
		private final String label;
		SortMode(String label) { this.label = label; }
		@Override public String toString() { return label; }
	}

	private static final Color GOLD = ZweetnekkenTheme.GOLD;
	private static final Color MUTED = ZweetnekkenTheme.MUTED;
	private static final String[][] POINT_RANKS = {
		{"Zealot", "150", "50"}, {"Saradominist", "400", "100"},
		{"Guthixian", "800", "200"}, {"Zamorakian", "1200", "300"},
		{"Serenist", "1800", "400"}, {"Bandosian", "2600", "500"},
		{"Zarosian", "3500", "600"}, {"Armadylean", "5000", "750"},
		{"Xerician", "7500", "1000"}
	};
	private final ZweetnekkenApiClient api;
	private final Consumer<String> onMemberSelected;
	private final JPanel entries = new JPanel();
	private final JTextField search = new JTextField();
	private final JComboBox<SortMode> sort = new JComboBox<>(SortMode.values());
	private List<ZweetnekkenModels.ClanLeaderboardMember> members = new ArrayList<>();
	private String message = "";
	private boolean loading;
	private long requestGeneration;

	ZweetnekkenMemberSearchPanel(ZweetnekkenApiClient api, Consumer<String> onMemberSelected)
	{
		this.api = api;
		this.onMemberSelected = onMemberSelected;
		setLayout(new BorderLayout());
		setOpaque(false);
		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setOpaque(false);
		body.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel heading = ZweetnekkenPanel.sectionHeading("LEADERBOARDS");
		heading.setFont(ZweetnekkenTheme.font(true));
		body.add(heading);
		body.add(Box.createVerticalStrut(9));
		sort.setSelectedItem(SortMode.PVM);
		sort.setBackground(ZweetnekkenTheme.SURFACE);
		sort.setForeground(ZweetnekkenTheme.TEXT);
		sort.setFont(ZweetnekkenTheme.font(false));
		sort.setRenderer(new DefaultListCellRenderer()
		{
			@Override public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
					int index, boolean isSelected, boolean cellHasFocus)
			{
				Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				component.setBackground(isSelected ? ZweetnekkenTheme.HOVER : ZweetnekkenTheme.SURFACE);
				component.setForeground(ZweetnekkenTheme.TEXT);
				component.setFont(ZweetnekkenTheme.font(false));
				return component;
			}
		});
		sort.setBorder(BorderFactory.createLineBorder(ZweetnekkenTheme.BORDER));
		sort.setUI(new BasicComboBoxUI()
		{
			@Override protected javax.swing.JButton createArrowButton()
			{
				BasicArrowButton button = new BasicArrowButton(SwingConstants.SOUTH,
						ZweetnekkenTheme.SURFACE, ZweetnekkenTheme.BORDER, GOLD, GOLD);
				button.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, ZweetnekkenTheme.BORDER));
				return button;
			}
		});
		sort.setMaximumSize(new Dimension(Integer.MAX_VALUE, 29));
		sort.setAlignmentX(Component.LEFT_ALIGNMENT);
		sort.addActionListener(e -> renderEntries());
		body.add(sort);
		body.add(Box.createVerticalStrut(8));
		JLabel searchLabel = new JLabel("Find a member");
		searchLabel.setForeground(ZweetnekkenTheme.TEXT);
		searchLabel.setFont(ZweetnekkenTheme.font(true));
		searchLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(searchLabel);
		body.add(Box.createVerticalStrut(4));
		search.setToolTipText("Search by RuneScape name");
		search.setBackground(ZweetnekkenTheme.SURFACE);
		search.setForeground(ZweetnekkenTheme.TEXT);
		search.setCaretColor(GOLD);
		search.setFont(ZweetnekkenTheme.font(false));
		search.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ZweetnekkenTheme.BORDER),
			BorderFactory.createEmptyBorder(3, 7, 3, 7)));
		search.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		search.setAlignmentX(Component.LEFT_ALIGNMENT);
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override public void insertUpdate(DocumentEvent e) { renderEntries(); }
			@Override public void removeUpdate(DocumentEvent e) { renderEntries(); }
			@Override public void changedUpdate(DocumentEvent e) { renderEntries(); }
		});
		body.add(search);
		body.add(Box.createVerticalStrut(14));
		entries.setLayout(new BoxLayout(entries, BoxLayout.Y_AXIS));
		entries.setOpaque(false);
		entries.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(entries);
		add(body, BorderLayout.NORTH);
		setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 8, 0));
		renderEntries();
	}

	void loadLeaderboard()
	{
		if (loading) return;
		loading = true;
		message = "Ranglijst laden…";
		long generation = ++requestGeneration;
		renderEntries();
		api.getClanLeaderboard(result -> SwingUtilities.invokeLater(() ->
		{
			if (generation != requestGeneration) return;
			loading = false;
			if (result.isSuccessful() && result.value != null)
			{
				members = result.value.members == null ? new ArrayList<>() : new ArrayList<>(result.value.members);
				members.removeIf(member -> member == null || member.name == null || member.name.isBlank());
				message = members.isEmpty() ? "Er zijn nog geen leden in de ranglijst." : "";
			}
			else
			{
				members = new ArrayList<>();
				message = result.statusCode == 404
					? "De ranglijst is nog niet beschikbaar op de Zweetnekken-server."
					: "De ranglijst kon niet worden geladen.";
			}
			renderEntries();
		}));
	}

	void refreshLeaderboard()
	{
		loading = false;
		loadLeaderboard();
	}

	private void renderEntries()
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(this::renderEntries);
			return;
		}
		entries.removeAll();
		if (loading || members.isEmpty())
		{
			entries.add(messageLabel(message.isEmpty() ? "Geen leden gevonden." : message));
			finish();
			return;
		}
		String query = search.getText().trim().toLowerCase(Locale.ROOT);
		List<ZweetnekkenModels.ClanLeaderboardMember> sorted = new ArrayList<>(members);
		sorted.sort(comparator((SortMode) sort.getSelectedItem()));
		boolean matched = false;
		int position = 0;
		for (ZweetnekkenModels.ClanLeaderboardMember member : sorted)
		{
			position++;
			if (matchesSearch(member, query))
			{
				matched = true;
				entries.add(memberRow(position, member));
				entries.add(Box.createVerticalStrut(3));
			}
		}
		if (!matched)
		{
			entries.add(messageLabel("Geen leden gevonden voor ‘" + search.getText().trim() + "’."));
		}
		finish();
	}

	static Comparator<ZweetnekkenModels.ClanLeaderboardMember> comparator(SortMode mode)
	{
		if (mode == SortMode.CLAN)
			return Comparator.comparingLong((ZweetnekkenModels.ClanLeaderboardMember m) -> m.clanPoints).reversed()
				.thenComparing(m -> m.name, String.CASE_INSENSITIVE_ORDER);
		if (mode == SortMode.RANK)
			return Comparator.comparingInt(ZweetnekkenMemberSearchPanel::rankOrder).reversed()
				.thenComparing(m -> m.name, String.CASE_INSENSITIVE_ORDER);
		return Comparator.comparingLong((ZweetnekkenModels.ClanLeaderboardMember m) -> m.pvmPoints).reversed()
			.thenComparing(m -> m.name, String.CASE_INSENSITIVE_ORDER);
	}

	static boolean matchesSearch(ZweetnekkenModels.ClanLeaderboardMember member, String query)
	{
		return query == null || query.isBlank()
			|| (member.name != null && member.name.toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT)));
	}

	private JPanel memberRow(int position, ZweetnekkenModels.ClanLeaderboardMember member)
	{
		JPanel row = ZweetnekkenTheme.surface(new BorderLayout(9, 0));
		row.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 11, 10, 11));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 61));
		JLabel place = new JLabel("#" + position, SwingConstants.LEFT);
		place.setForeground(position <= 3 ? GOLD : MUTED);
		place.setFont(ZweetnekkenTheme.font(true));
		// Keep enough room for three-digit places; otherwise Swing shortens values such as #20 to #….
		place.setPreferredSize(new Dimension(42, 34));
		JPanel west = new JPanel();
		west.setOpaque(false);
		west.setLayout(new BoxLayout(west, BoxLayout.X_AXIS));
		west.add(place);
		row.add(west, BorderLayout.WEST);
		JPanel details = new JPanel();
		details.setOpaque(false);
		details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
		JLabel name = new JLabel(member.name);
		name.setForeground(ZweetnekkenTheme.TEXT);
		name.setFont(ZweetnekkenTheme.font(true));
		name.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		name.setToolTipText("Open het profiel van " + member.name);
		name.addMouseListener(new MouseAdapter()
		{
			@Override public void mouseClicked(MouseEvent e)
			{
				if (e.getButton() == MouseEvent.BUTTON1 && onMemberSelected != null)
					onMemberSelected.accept(member.name);
			}

			@Override public void mouseEntered(MouseEvent e) { name.setForeground(GOLD); }
			@Override public void mouseExited(MouseEvent e) { name.setForeground(ZweetnekkenTheme.TEXT); }
		});
		JLabel rank = new JLabel(displayRank(member));
		rank.setForeground(MUTED);
		rank.setFont(ZweetnekkenTheme.font(false));
		details.add(name);
		details.add(rank);
		row.add(details, BorderLayout.CENTER);
		String score = selectedScoreText(member);
		if (!score.isEmpty())
		{
			JLabel points = new JLabel(score, SwingConstants.RIGHT);
			points.setForeground(GOLD);
			points.setFont(ZweetnekkenTheme.font(true));
			row.add(points, BorderLayout.EAST);
		}
		return row;
	}

	private long selectedScore(ZweetnekkenModels.ClanLeaderboardMember member)
	{
		SortMode selected = (SortMode) sort.getSelectedItem();
		return selected == SortMode.CLAN ? member.clanPoints : selected == SortMode.RANK
			? rankOrder(member) : member.pvmPoints;
	}

	private String selectedScoreText(ZweetnekkenModels.ClanLeaderboardMember member)
	{
		if (sort.getSelectedItem() == SortMode.RANK)
			return "";
		return NumberFormat.getIntegerInstance(Locale.US).format(selectedScore(member));
	}

	static String displayRank(ZweetnekkenModels.ClanLeaderboardMember member)
	{
		return displayRank(member.currentRank, member.pvmPoints, member.clanPoints);
	}

	static String displayRank(String storedRank, long pvmPoints, long clanPoints)
	{
		String stored = storedRank == null ? "" : storedRank.trim();
		if (stored.equalsIgnoreCase("Clan Owner") || stored.equalsIgnoreCase("Clan Leader"))
		{
			ZweetnekkenModels.ClanLeaderboardMember member = new ZweetnekkenModels.ClanLeaderboardMember();
			member.pvmPoints = pvmPoints;
			member.clanPoints = clanPoints;
			int rank = pointRankIndex(member);
			return rank >= 0 ? POINT_RANKS[rank][0] : "Zweetnek";
		}
		return stored.isEmpty() ? "—" : stored;
	}

	private static int rankOrder(ZweetnekkenModels.ClanLeaderboardMember member)
	{
		String stored = member.currentRank == null ? "" : member.currentRank.trim();
		if (stored.equalsIgnoreCase("Clan Owner") || stored.equalsIgnoreCase("Clan Leader"))
			return pointRankIndex(member) + 1;
		return member.clanRankOrder;
	}

	private static int pointRankIndex(ZweetnekkenModels.ClanLeaderboardMember member)
	{
		int rank = -1;
		for (int i = 0; i < POINT_RANKS.length; i++)
		{
			if (member.pvmPoints >= Long.parseLong(POINT_RANKS[i][1])
				&& member.clanPoints >= Long.parseLong(POINT_RANKS[i][2])) rank = i;
		}
		return rank;
	}

	private static JLabel messageLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(MUTED);
		label.setFont(ZweetnekkenTheme.font(false));
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private void finish()
	{
		ZweetnekkenTheme.typography(this);
		entries.revalidate();
		entries.repaint();
		revalidate();
		repaint();
	}
}

