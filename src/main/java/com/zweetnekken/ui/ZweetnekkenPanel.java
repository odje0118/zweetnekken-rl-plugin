/*
 * Portions of this panel are adapted from the PB Tracker Sync, Llama Club, and
 * Terpinheimer plugin panels. See LICENSES/pb-tracker-sync-LICENSE.txt,
 * LICENSES/llama-LICENSE.txt, and LICENSES/terpinheimer-LICENSE.txt.
 */
package com.zweetnekken.ui;

import com.zweetnekken.ZweetnekkenConfig;
import com.zweetnekken.evidence.EventPipeline;
import com.zweetnekken.evidence.EvidenceStore;
import com.zweetnekken.model.ZweetnekkenModels;
import com.zweetnekken.service.ZweetnekkenDataService;
import com.zweetnekken.service.TileRaceService;
import com.zweetnekken.service.ImageCache;
import com.zweetnekken.api.ZweetnekkenApiClient;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Image;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;

public class ZweetnekkenPanel extends PluginPanel {
	private static final Color GOLD = ZweetnekkenTheme.GOLD;
	private static final Color PANEL_BACKGROUND = ZweetnekkenTheme.BACKGROUND;
	private static final Color CARD_BACKGROUND = ZweetnekkenTheme.SURFACE;
	private static final Color TEXT_MUTED = ZweetnekkenTheme.MUTED;
	private static final Color TAB_BG = new Color(30, 33, 36);
	private static final Color TAB_ACTIVE_BG = ZweetnekkenTheme.BLUE;
	private static final Color TAB_ACTIVE_FG = Color.WHITE;
	private static final Color TAB_INACTIVE_FG = new Color(170, 170, 170);
	private static final Color TAB_HOVER_BG = ZweetnekkenTheme.HOVER;
	private static final Color CONNECTED_COLOR = ZweetnekkenTheme.GREEN;
	private static final Color DISCONNECTED_COLOR = new Color(158, 158, 158);
	private static final Color BOTW_ACCENT = new Color(176, 70, 67);
	private static final Color SOTW_ACCENT = new Color(55, 142, 157);
	private static final Color PVM_ACCENT = ZweetnekkenTheme.BLUE;
	private static final Color CLAN_ACCENT = ZweetnekkenTheme.GREEN;
	private static final Color PRIMARY_BUTTON_BG = PANEL_BACKGROUND;
	private static final Color SCORE_COLOR = new Color(238, 205, 124);
	static final int NAV_BUTTON_HEIGHT = 28;
	private static final Color[] RANK_COLORS = {
			new Color(238, 193, 73), new Color(185, 195, 204), new Color(190, 126, 75)
	};
	private final ZweetnekkenDataService data;
	private final ImageCache imageCache;
	private final EvidenceStore evidence;
	private final EventPipeline pipeline;
	private final ZweetnekkenConfig config;
	private final ConfigManager configManager;
	private final SpriteManager spriteManager;
	private final ItemManager itemManager;
	private final TileRaceService tileRace;
	private final Runnable dataListener = this::refreshOnEdt;
	private final AtomicBoolean refreshQueued = new AtomicBoolean();
	private final AtomicLong bannerRefreshGeneration = new AtomicLong();
	private boolean submissionsLoading;
	private boolean submissionsReloadRequested;
	private final JLabel banner = new AspectRatioIconLabel();
	private final JLabel avatar = new JLabel();
	private JPanel profileBar;
	private final JLabel identity = new JLabel("Log in op RuneLite", SwingConstants.LEFT);
	private final JLabel status = muted("");
	private final JLabel connectionDot = new JLabel();
	private final JPanel home = fullWidthColumn();
	private final EventPanel eventPanel;
	private final JPanel submissions = verticalPanel();
	private final JPanel settings = verticalPanel();
	private final ZweetnekkenPvmDiaryPanel pvmDiaryPanel;
	private final ZweetnekkenMemberSearchPanel memberSearchPanel;
	private final ZweetnekkenMemberProfilePanel memberProfilePanel;
	private final CardLayout contentCards = new CardLayout();
	private final JPanel contentPanel = new JPanel(contentCards);
	private final java.util.Map<String, JLabel> navigationTabs = new java.util.LinkedHashMap<>();
	private final JPanel extraSettings = verticalPanel();
	private boolean botwExpanded;
	private boolean sotwExpanded;
	private String activeTab = "Profile";

	@Inject
	public ZweetnekkenPanel(ZweetnekkenDataService data, EvidenceStore evidence, EventPipeline pipeline,
			ZweetnekkenConfig config, ConfigManager configManager, SpriteManager spriteManager, ItemManager itemManager,
			ImageCache imageCache, TileRaceService tileRace, ZweetnekkenApiClient api) {
		super(false);
		this.data = data;
		this.imageCache = imageCache;
		this.evidence = evidence;
		this.pipeline = pipeline;
		this.config = config;
		this.configManager = configManager;
		this.spriteManager = spriteManager;
		this.itemManager = itemManager;
		this.tileRace = tileRace;
		this.eventPanel = new EventPanel(tileRace::currentEvents);
		this.pvmDiaryPanel = new ZweetnekkenPvmDiaryPanel(data);
		this.memberProfilePanel = new ZweetnekkenMemberProfilePanel(data, this::returnToLeaderboards);
		this.memberSearchPanel = new ZweetnekkenMemberSearchPanel(api, this::openMemberProfile);
		this.eventPanel.setVisible(false);
		tileRace.addListener(this::refreshEventPanel);
		setLayout(new BorderLayout());
		setBackground(PANEL_BACKGROUND);
		setBorder(null);

		JPanel header = new JPanel(new GridBagLayout());
		header.setBackground(PANEL_BACKGROUND);
		GridBagConstraints headerRow = new GridBagConstraints();
		headerRow.gridx = 0;
		headerRow.gridy = 0;
		headerRow.weightx = 1.0;
		headerRow.fill = GridBagConstraints.HORIZONTAL;
		banner.setAlignmentX(Component.CENTER_ALIGNMENT);
		banner.setIcon(new ImageIcon(fallbackBanner()));
		refreshBanner();
		header.add(banner, headerRow);

		// Profile bar with connection status dot
		profileBar = ZweetnekkenTheme.surface(new BorderLayout(8, 0));
		JPanel profile = profileBar;
		profile.setBackground(CARD_BACKGROUND);
		profile.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		avatar.setPreferredSize(new Dimension(36, 36));
		avatar.setIcon(new ImageIcon(scale(placeholderAvatar(), 36, 36)));
		profile.add(avatar, BorderLayout.WEST);

		JPanel text = verticalPanel();
		text.setOpaque(false);
		text.setBackground(CARD_BACKGROUND);
		identity.setForeground(Color.WHITE);
		identity.setOpaque(false);
		identity.setFont(ZweetnekkenTheme.font(true));
		text.add(identity);
		status.setFont(ZweetnekkenTheme.font(false));
		status.setOpaque(false);
		text.add(status);
		profile.add(text, BorderLayout.CENTER);

		connectionDot.setPreferredSize(new Dimension(14, 14));
		connectionDot.setToolTipText("Niet verbonden");
		connectionDot.setHorizontalAlignment(SwingConstants.CENTER);
		connectionDot.setVerticalAlignment(SwingConstants.CENTER);
		profile.add(connectionDot, BorderLayout.EAST);

		// Compact two-column navigation for the plugin pages.
		JPanel navigation = fullWidthVerticalPanel();
		navigation.setLayout(new GridBagLayout());
		navigation.setBackground(PANEL_BACKGROUND);
		navigation.setAlignmentX(Component.LEFT_ALIGNMENT);
		GridBagConstraints navigationRow = new GridBagConstraints();
		navigationRow.gridx = 0;
		navigationRow.gridy = 0;
		navigationRow.weightx = 1.0;
		navigationRow.fill = GridBagConstraints.HORIZONTAL;
		navigation.add(buildTabBar(), navigationRow);
		headerRow.gridy++;
		header.add(navigation, headerRow);

		// Content area with CardLayout
		contentPanel.setBackground(PANEL_BACKGROUND);
		contentPanel.add(scroll(home, true), "Profile");
		contentPanel.add(scroll(memberSearchPanel, true), "Leaderboards");
		contentPanel.add(scroll(memberProfilePanel, true), "MemberProfile");
		contentPanel.add(scroll(pvmDiaryPanel, true), "PvM Diary");
		contentPanel.add(scroll(submissions, true), "Submissions");
		settings.add(buildExtraSettings());
		contentPanel.add(scroll(settings, true), "Settings");
		contentCards.show(contentPanel, "Profile");

		add(header, BorderLayout.NORTH);
		add(contentPanel, BorderLayout.CENTER);
		data.addListener(dataListener);
		pipeline.addListener(dataListener);
		refresh();
	}

	private JPanel buildExtraSettings() {
		JPanel container = card();
		container.setAlignmentX(Component.LEFT_ALIGNMENT);
		container.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		container.add(sectionHeading("INSTELLINGEN"));
		container.add(Box.createVerticalStrut(8));
		extraSettings.setAlignmentX(Component.LEFT_ALIGNMENT);
		extraSettings.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		extraSettings.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		extraSettings.setVisible(true);
		extraSettings.add(sectionHeading("Profiel & uploads"));
		extraSettings.add(Box.createVerticalStrut(7));
		JPanel actions = fullWidthVerticalPanel();
		actions.add(configActionButton("Profiel vernieuwen", "refreshProfile", true));
			actions.add(blackDivider(6));
		actions.add(configActionButton("Uploads opnieuw proberen", "retryOutbox", false));
			actions.add(blackDivider(6));
		actions.add(configActionButton("Afbeeldingencache wissen", "clearImageCache", false));
		extraSettings.add(actions);
		container.add(extraSettings);
		return container;
	}

	private JButton configActionButton(String text, String key, boolean primary) {
		JButton button = actionButton(text, primary);
		button.addActionListener(e -> {
			if ("refreshProfile".equals(key)) memberSearchPanel.refreshLeaderboard();
			// Always generate a change event, even if an older plugin version left this key
			// set.
			configManager.setConfiguration(ZweetnekkenConfig.GROUP, key, false);
			configManager.setConfiguration(ZweetnekkenConfig.GROUP, key, true);
		});
		return button;
	}

	private JPanel buildTabBar() {
		JPanel bar = new JPanel(new GridBagLayout());
		bar.setOpaque(false);
		bar.setBorder(BorderFactory.createEmptyBorder(8, 8, 2, 8));
		int tabBarHeight = NAV_BUTTON_HEIGHT * 3 + 24;
		bar.setMinimumSize(new Dimension(0, tabBarHeight));
		bar.setPreferredSize(new Dimension(0, tabBarHeight));
		bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, tabBarHeight));

		String[] names = {"Profile", "Leaderboards", "PvM Diary", "Submissions", "Settings"};
		String[] labels = {"Profiel", "Leaderboards", "PvM Diary", "Inzendingen", "Instellingen"};
		for (int i = 0; i < names.length; i++)
		{
			String name = names[i];
			JLabel tab = createTabLabel(labels[i], name.equals(activeTab));
			navigationTabs.put(name, tab);
			tab.addMouseListener(new MouseAdapter() {
				@Override public void mouseClicked(MouseEvent e) { switchTab(name); }
				@Override public void mouseEntered(MouseEvent e) {
					if (!activeTab.equals(name)) tab.setBackground(TAB_HOVER_BG);
					tab.repaint();
				}
				@Override public void mouseExited(MouseEvent e) {
					if (!activeTab.equals(name)) tab.setBackground(TAB_BG);
					tab.repaint();
				}
			});
			GridBagConstraints cell = new GridBagConstraints();
			cell.gridx = i % 2;
			cell.gridy = i / 2;
			cell.gridwidth = i == names.length - 1 ? 2 : 1;
			cell.weightx = cell.gridwidth;
			cell.fill = GridBagConstraints.HORIZONTAL;
			cell.insets = new Insets(i < 2 ? 0 : 5,
					i % 2 == 1 ? 6 : 0, 0, i % 2 == 0 && cell.gridwidth == 1 ? 6 : 0);
			bar.add(tab, cell);
		}
		return bar;
	}

	private static JLabel createTabLabel(String text, boolean active) {
		JLabel label = new JLabel(text, SwingConstants.CENTER) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(getBackground());
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
				g2.dispose();
				super.paintComponent(g);
			}
		};
		label.setOpaque(false);
		label.setFont(ZweetnekkenTheme.font(true));
		label.setMinimumSize(new Dimension(0, NAV_BUTTON_HEIGHT));
		label.setPreferredSize(new Dimension(0, NAV_BUTTON_HEIGHT));
		label.setMaximumSize(new Dimension(Integer.MAX_VALUE, NAV_BUTTON_HEIGHT));
		label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		if (active) {
			label.setBackground(TAB_ACTIVE_BG);
			label.setForeground(TAB_ACTIVE_FG);
		} else {
			label.setBackground(TAB_BG);
			label.setForeground(TAB_INACTIVE_FG);
		}
		return label;
	}

	private void switchTab(String tabName) {
		if (activeTab.equals(tabName)) {
			if ("Leaderboards".equals(tabName)) contentCards.show(contentPanel, "Leaderboards");
			return;
		}
		activeTab = tabName;

		navigationTabs.forEach((name, tab) -> {
			boolean selected = name.equals(tabName);
			tab.setBackground(selected ? TAB_ACTIVE_BG : TAB_BG);
			tab.setForeground(selected ? TAB_ACTIVE_FG : TAB_INACTIVE_FG);
			tab.repaint();
		});
		contentCards.show(contentPanel, tabName);
		if ("PvM Diary".equals(tabName)) pvmDiaryPanel.refresh();
		if ("Leaderboards".equals(tabName)) memberSearchPanel.loadLeaderboard();
	}

	private void openMemberProfile(String memberName)
	{
		memberProfilePanel.load(memberName);
		contentCards.show(contentPanel, "MemberProfile");
		contentPanel.revalidate();
		contentPanel.repaint();
	}

	private void returnToLeaderboards(String memberName)
	{
		contentCards.show(contentPanel, "Leaderboards");
		contentPanel.revalidate();
		contentPanel.repaint();
	}

	private void refreshOnEdt() {
		if (!refreshQueued.compareAndSet(false, true))
			return;
		SwingUtilities.invokeLater(() -> {
			refreshQueued.set(false);
			refresh();
		});
	}

	private void refreshEventPanel() { eventPanel.refresh(); }

	public void setEventPanelVisible(boolean visible) {
		Runnable update = () -> {
			eventPanel.setVisible(visible);
			if (visible) eventPanel.refresh();
			home.revalidate();
			home.repaint();
		};
		if (SwingUtilities.isEventDispatchThread()) update.run();
		else SwingUtilities.invokeLater(update);
	}

	public void refreshBanner() {
		long requestId = bannerRefreshGeneration.incrementAndGet();
		if (config.animatedBanner()) {
			String animatedUrl = ZweetnekkenApiClient.ZWEETNEKKEN_API_ORIGIN + "/zweetnekken-banner.gif";
			imageCache.loadWebsiteIcon("zweetnekken-banner-animated", animatedUrl, icon -> {
				if (requestId != bannerRefreshGeneration.get()) return;
				if (icon != null) setBannerIcon(icon, requestId);
				else refreshStaticBanner(requestId);
			});
			return;
		}
		refreshStaticBanner(requestId);
	}

	/** Immediately invalidates any older banner request before an image-cache clear. */
	public void invalidateBannerLoads() {
		long requestId = bannerRefreshGeneration.incrementAndGet();
		setBannerIcon(new ImageIcon(fallbackBanner()), requestId);
	}

	private void refreshStaticBanner(long requestId) {
		String bannerUrl = ZweetnekkenApiClient.ZWEETNEKKEN_API_ORIGIN + "/zweetnekken-banner.jpg";
		imageCache.loadWebsiteImage("zweetnekken-banner", bannerUrl, image -> {
			if (requestId != bannerRefreshGeneration.get()) return;
			BufferedImage selected = image == null ? fallbackBanner() : image;
			setBannerIcon(new ImageIcon(selected), requestId);
		});
	}

	private void setBannerIcon(Icon icon, long requestId) {
		Runnable update = () -> {
			if (requestId != bannerRefreshGeneration.get()) return;
			if (icon instanceof ImageIcon) {
				// Explicitly attach the banner label as the image observer so AWT
				// receives animated GIF frame updates after scaling in paintComponent.
				((ImageIcon) icon).setImageObserver(banner);
			}
			banner.setIcon(icon);
			((AspectRatioIconLabel) banner).refreshAspectRatio();
			banner.revalidate();
			banner.repaint();
		};
		if (SwingUtilities.isEventDispatchThread()) update.run();
		else SwingUtilities.invokeLater(update);
	}

	private void refresh() {
		ZweetnekkenModels.Profile profile = data.profile();
		if (profile == null || profile.member == null) {
			identity.setText("Zweetnekken");
			identity.setToolTipText(data.message());
			status.setText("<html>" + esc(data.message()) + "</html>");
			avatar.setIcon(new ImageIcon(scale(placeholderAvatar(), 36, 36)));
		} else {
			String rank = profile.standings == null ? "—" : value(profile.standings.currentRank);
			identity.setToolTipText(null);
			identity.setText("<html><b>" + esc(profile.member.name) + "</b><br><span style='color:#ffffff'>"
					+ esc(rank) + "</span></html>");
			status.setText("<html><span style='color:#96968c'>Zweetnekken</span></html>");
			avatar.setIcon(new ImageIcon(
					scale(data.profileImage() == null ? placeholderAvatar() : data.profileImage(), 36, 36)));
		}
		updateConnectionDot();
		rebuildHome();
		rebuildSubmissions();
		pvmDiaryPanel.refresh();
		ZweetnekkenTheme.typography(this);
		revalidate();
		repaint();
	}

	private void updateConnectionDot() {
		ZweetnekkenDataService.Connection conn = data.connection();
		boolean connected = conn == ZweetnekkenDataService.Connection.CONNECTED;
		Color dotColor = connected ? CONNECTED_COLOR : DISCONNECTED_COLOR;
		String tooltip;
		switch (conn) {
			case CONNECTED:
				tooltip = "Verbonden";
				break;
			case CHECKING:
				tooltip = "Code controleren…";
				break;
			case INVALID:
				tooltip = "Ongeldige code";
				break;
			case UNAVAILABLE:
				tooltip = "Niet beschikbaar";
				break;
			default:
				tooltip = "Niet verbonden";
		}
		connectionDot.setToolTipText(tooltip);
		connectionDot.setIcon(new ImageIcon(createDotImage(dotColor)));
	}

	private static BufferedImage createDotImage(Color color) {
		int size = 10;
		BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(color);
		g.fillOval(0, 0, size, size);
		g.dispose();
		return img;
	}

	private void rebuildHome() {
		home.removeAll();
		home.add(profileBar);
		home.add(blackDivider(8));
		ZweetnekkenModels.Profile profile = data.profile();
		if (profile != null && profile.standings != null) {
			ZweetnekkenModels.Standings s = profile.standings;
			JPanel ranks = card();
			if (s.nextRank != null) {
				ranks.add(nextRank(value(s.nextRank.name)));
				ranks.add(Box.createVerticalStrut(5));
				ranks.add(progress("PvM", s.pvmPoints, s.nextRank.pvmPointsRemaining, PVM_ACCENT));
				ranks.add(Box.createVerticalStrut(5));
				ranks.add(progress("Clan", s.clanPoints, s.nextRank.clanPointsRemaining, CLAN_ACCENT));
			} else {
				ranks.add(sectionHeading("CLANPROFIEL"));
				ranks.add(Box.createVerticalStrut(7));
				ranks.add(statRow("PVM RANK", rank(s.pvmRank), PVM_ACCENT,
						"CLAN RANK", rank(s.clanRank), CLAN_ACCENT));
				ranks.add(Box.createVerticalStrut(8));
				ranks.add(muted("Beheerdersrang of hoogste rang"));
			}
			home.add(ranks);
			home.add(blackDivider(8));
		}
		home.add(pvmDiaryOverview());
		home.add(blackDivider(8));
		home.add(collectionLogOverview(profile == null ? null : profile.collectionLog));
		home.add(blackDivider(10));
		home.add(competitionSectionHeading("COMPETITIES", GOLD));
		home.add(Box.createVerticalStrut(5));
		ZweetnekkenModels.CompetitionPanels competitions = data.competitionPanels();
		ZweetnekkenModels.Standings standings = profile == null ? null : profile.standings;
		String playerName = profile == null || profile.member == null ? null : profile.member.name;
		String weeklyTiming = sharedCompetitionTiming(competitions);
		if (!weeklyTiming.isEmpty()) {
			home.add(weeklyTimer(weeklyTiming));
			home.add(blackDivider(8));
		}
		boolean authRequired = requiresCompetitionAuthentication(data.connection());
		home.add(competition("BOTW", competitions == null ? null : competitions.botw,
				authRequired ? data.unauthenticatedBotwImage() : data.botwImage(),
				standings == null ? null : standings.botwRank, playerName, botwExpanded));
		home.add(blackDivider(10));
		home.add(competition("SOTW", competitions == null ? null : competitions.sotw,
				authRequired ? data.unauthenticatedSotwImage() : data.sotwImage(),
				standings == null ? null : standings.sotwRank, playerName, sotwExpanded));
		home.add(blackDivider(8));
		home.add(eventPanel);
	}

	private JPanel pvmDiaryOverview()
	{
		JPanel diary = card();
		diary.add(label("PvM Diary", true));
		diary.add(Box.createVerticalStrut(5));
		ZweetnekkenModels.PvmDiaryStatus status = data.pvmDiaryStatus();
		if (status == null)
		{
			diary.add(wrappedMuted(data.pvmDiaryMessage()));
		}
		else
		{
		diary.add(row("Punten", number(status.earnedPoints), "Tiers",
				status.completedTiers + " / " + status.totalTiers));
			JProgressBar progress = new JProgressBar(0, 1000);
			progress.setValue(status.totalPoints <= 0 ? 0
				: (int) Math.min(1000, Math.max(0, status.earnedPoints * 1000 / status.totalPoints)));
			progress.setBorderPainted(false);
			progress.setBackground(new Color(52, 55, 58));
			progress.setForeground(GOLD);
			progress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 7));
			diary.add(Box.createVerticalStrut(7));
			diary.add(progress);
		}
		diary.add(Box.createVerticalStrut(8));
		JButton open = actionButton("Bekijk PvM Diary", false);
		open.setAlignmentX(Component.LEFT_ALIGNMENT);
		open.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		open.addActionListener(e -> switchTab("PvM Diary"));
		diary.add(open);
		return diary;
	}

	private JPanel collectionLogOverview(ZweetnekkenModels.CollectionLogProgress progress)
	{
		JPanel collectionLog = card();
		collectionLog.add(label("Collection Log", true));
		collectionLog.add(Box.createVerticalStrut(6));
		if (progress == null)
		{
			collectionLog.add(wrappedMuted("Collection Log is nog niet gesynchroniseerd."));
			return collectionLog;
		}
		collectionLog.add(row("Slots obtained", number(progress.obtainedCount), "of", number(progress.totalCount)));
		JProgressBar bar = new JProgressBar(0, 1000);
		bar.setValue(progress.totalCount <= 0 ? 0 : (int) Math.min(1000,
			Math.max(0, progress.obtainedCount * 1000L / progress.totalCount)));
		bar.setBorderPainted(false);
		bar.setBackground(new Color(52, 55, 58));
		bar.setForeground(GOLD);
		bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 7));
		collectionLog.add(Box.createVerticalStrut(6));
		collectionLog.add(bar);
		String syncedAt = collectionLogSyncedAt(progress.updatedAt);
		if (!syncedAt.isEmpty())
		{
			collectionLog.add(Box.createVerticalStrut(5));
			collectionLog.add(muted("Synced " + syncedAt));
		}
		return collectionLog;
	}

	private static String collectionLogSyncedAt(String timestamp)
	{
		if (timestamp == null || timestamp.isBlank()) return "";
		try
		{
			return DateTimeFormatter.ofPattern("MMM d, HH:mm z", Locale.ENGLISH)
				.format(Instant.parse(timestamp).atZone(ZoneId.systemDefault()));
		}
		catch (RuntimeException e)
		{
			return "";
		}
	}

	static boolean requiresCompetitionAuthentication(ZweetnekkenDataService.Connection connection) {
		return connection == ZweetnekkenDataService.Connection.NOT_CONFIGURED
				|| connection == ZweetnekkenDataService.Connection.INVALID;
	}

	private JPanel competition(String kind, ZweetnekkenModels.CompetitionPanel competition, BufferedImage image,
			ZweetnekkenModels.CompetitionRank playerRank, String playerName, boolean expanded) {
		JPanel card = card();
		JButton toggle = sectionToggleButton(kind, expanded);
		toggle.addActionListener(e -> toggleCompetition(kind));
		card.add(toggle);
		if (!expanded)
			return card;

		BufferedImage scaledArtwork = image == null ? placeholderCompetition(kind) : image;
		JLabel artwork = new AspectRatioIconLabel();
		artwork.setIcon(new ImageIcon(scaledArtwork));
		artwork.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(artwork);
		card.add(Box.createVerticalStrut(5));
		boolean upcoming = isUpcoming(competition);
		boolean finished = isFinished(competition);
		if (competition == null) {
			card.add(muted("Competitiegegevens zijn momenteel niet beschikbaar"));
			return card;
		}
		if (upcoming) {
			Color accent = competitionAccent(kind);
			card.add(competitionSectionHeading("BINNENKORT", accent));
			card.add(Box.createVerticalStrut(3));
			card.add(muted(startsCountdown(competition.startsAt)));
			return card;
		}
		List<ZweetnekkenModels.PanelLeader> leaders = competition.leaders == null ? java.util.Collections.emptyList()
				: competition.leaders;
		long leadingScore = leaders.stream().mapToLong(leader -> Math.max(0, leader.gained)).max().orElse(0);
		Color accent = competitionAccent(kind);
		boolean showPersonalProgress = !finished
				|| (playerRank != null && playerRank.rank != null && playerRank.rank <= 3);
		if (showPersonalProgress) {
			card.add(competitionSectionHeading("JOUW VOORTGANG", accent));
			card.add(Box.createVerticalStrut(3));
			if (playerRank == null || playerRank.rank == null) {
				card.add(muted("Nog geen persoonlijke voortgang"));
			} else {
				String score = CompetitionScoreFormatter.format(playerRank.gained,
					playerRank.unit == null ? competition.unit : playerRank.unit, playerRank.displayValue);
				card.add(leaderRow(playerRank.rank, value(playerName), score,
						leadingScore == 0 ? 0 : (double) Math.max(0, playerRank.gained) / leadingScore, accent, true));
			}
			card.add(Box.createVerticalStrut(6));
		}

		card.add(competitionSectionHeading(finished ? "WINNAARS" : "RANGLIJST", SCORE_COLOR));
		card.add(Box.createVerticalStrut(3));
		for (int i = 0; i < Math.min(3, leaders.size()); i++) {
			ZweetnekkenModels.PanelLeader leader = leaders.get(i);
			String nameValue = leader.displayName == null || leader.displayName.isBlank() ? value(leader.username)
					: leader.displayName;
			String score = CompetitionScoreFormatter.format(leader.gained,
				leader.unit == null ? competition.unit : leader.unit, leader.displayValue);
			card.add(leaderRow(leader.rank > 0 ? leader.rank : i + 1, nameValue, score,
					leadingScore == 0 ? 0 : (double) Math.max(0, leader.gained) / leadingScore, accent, false));
			if (i < Math.min(3, leaders.size()) - 1)
				card.add(Box.createVerticalStrut(3));
		}
		if (leaders.isEmpty()) {
			card.add(muted(finished ? "Nog geen afgeronde competities" : "Nog geen ranglijstgegevens"));
		}
		return card;
	}

	private static Color competitionAccent(String kind) {
		return "BOTW".equals(kind) ? BOTW_ACCENT : SOTW_ACCENT;
	}

	private void toggleCompetition(String kind) {
		if ("BOTW".equals(kind))
			botwExpanded = !botwExpanded;
		else
			sotwExpanded = !sotwExpanded;
		rebuildHome();
		home.revalidate();
		home.repaint();
	}

	private void rebuildSubmissions() {
		if (submissionsLoading) {
			submissionsReloadRequested = true;
			return;
		}
		submissionsLoading = true;
		new SwingWorker<List<EvidenceStore.Record>, Void>() {
			@Override protected List<EvidenceStore.Record> doInBackground() {
				List<EvidenceStore.Record> records = evidence.records();
				records.removeIf(record -> record.metadata != null && "diary".equals(record.metadata.eventType));
				return records;
			}

			@Override protected void done() {
				submissionsLoading = false;
				if (submissionsReloadRequested) {
					submissionsReloadRequested = false;
					rebuildSubmissions();
					return;
				}
				try {
					renderSubmissions(get());
				} catch (InterruptedException | java.util.concurrent.ExecutionException e) {
					submissions.removeAll();
					submissions.add(muted("Lokale inzendingen konden niet worden geladen."));
				}
				submissions.revalidate();
				submissions.repaint();
			}
		}.execute();
	}

	private void renderSubmissions(List<EvidenceStore.Record> records) {
		submissions.removeAll();
		boolean trialistMode = data.isCurrentPlayerPluginEligible() && !data.canSubmitLoot();
		List<EvidenceStore.Record> visibleRecords = new java.util.ArrayList<>(records);
		// Hide legacy test-only events from older plugin versions.
		visibleRecords.removeIf(item -> item != null && item.metadata != null
			&& "loot_test".equalsIgnoreCase(item.metadata.eventType));
		if (trialistMode) {
			submissions.add(wrappedMuted("Als Trialist kan je nog geen drops submitten."));
			submissions.add(Box.createVerticalStrut(6));
			// Older versions could leave failed loot or test-loot events in the local
			// evidence store. Do not show them as retryable submissions to Trialists.
			visibleRecords.removeIf(item -> item != null && item.metadata != null
				&& ("loot".equalsIgnoreCase(item.metadata.eventType)
					|| "loot_test".equalsIgnoreCase(item.metadata.eventType)));
		}
		if (visibleRecords.isEmpty()) {
			if (!trialistMode) {
				submissions.add(muted(
					"Drops, PB's, collection-logitems, pets en combat achievements verschijnen hier."));
			}
			return;
		}
		for (List<EvidenceStore.Record> group : submissionGroups(visibleRecords).values()) {
			EvidenceStore.Record record = preferredRecord(group);
			ZweetnekkenModels.EventStatus displayStatus = groupStatus(group);
			JPanel card = card();
			card.add(submissionHeading(record, displayStatus, group));
			if (record.error != null)
				card.add(wrappedMuted(record.error));
			JSpinner party = new JSpinner(new SpinnerNumberModel(
					record.metadata.party == null ? 1 : record.metadata.party.submittedPartySize, 1, 100, 1));
			party.setPreferredSize(new Dimension(54, party.getPreferredSize().height));
			int initialClan = record.metadata.party == null ? 0 : record.metadata.party.submittedClanMemberCount;
			int initialNonClan = record.metadata.party == null ? 0 : record.metadata.party.submittedNonClanMemberCount;
			JSpinner clanMembers = new JSpinner(new SpinnerNumberModel(initialClan, 0, 100, 1));
			JSpinner nonClanMembers = new JSpinner(new SpinnerNumberModel(initialNonClan, 0, 100, 1));
			clanMembers.setPreferredSize(new Dimension(54, clanMembers.getPreferredSize().height));
			nonClanMembers.setPreferredSize(new Dimension(54, nonClanMembers.getPreferredSize().height));
			JTextField notes = new JTextField();
			notes.setToolTipText("Optionele notities bij de inzending");
			notes.setMaximumSize(new Dimension(Integer.MAX_VALUE, notes.getPreferredSize().height));
			PartyMemberEditor partyMemberEditor = record.metadata.party == null ? null
					: new PartyMemberEditor(record.metadata.party);
			if (record.metadata.party != null) {
				card.add(Box.createVerticalStrut(4));
				card.add(fieldRow("Groepsgrootte", party));
				card.add(fieldRow("Clanleden", clanMembers));
				card.add(fieldRow("Gastspelers", nonClanMembers));
				JButton editParty = actionButton("Groepsleden aanpassen", false);
				editParty.addActionListener(e -> partyMemberEditor.show(party, clanMembers, nonClanMembers));
				card.add(editParty);
				card.add(muted("Herkend via " + friendlyMethod(record.metadata.party.method)));
				int unknown = Math.max(0, record.metadata.party.detectedPartySize
						- record.metadata.party.detectedClanMemberCount
						- record.metadata.party.detectedNonClanMemberCount);
				if (unknown > 0 && !"fixed_solo".equals(record.metadata.party.method))
					card.add(muted(unknown + (unknown == 1
							? " deelnemer kon niet worden ingedeeld" : " deelnemers konden niet worden ingedeeld")));
				String memberNames = partyMemberNames(record.metadata.party);
				if (!memberNames.isEmpty())
					card.add(wrappedMuted(memberNames));
			}
			if (displayStatus == ZweetnekkenModels.EventStatus.DRAFT && canFinalize(group)) {
				card.add(Box.createVerticalStrut(4));
				card.add(label("Notities", false));
				card.add(notes);
			}
			List<JButton> actionButtons = new java.util.ArrayList<>();
			EvidenceStore.Record proofRecord = proofRecord(group, record);
			if (proofRecord != null) {
				JButton proof = actionButton("Bewijs bekijken", false);
				proof.addActionListener(e -> showProof(proofRecord));
				actionButtons.add(proof);
			}
			if (displayStatus == ZweetnekkenModels.EventStatus.FAILED
					|| displayStatus == ZweetnekkenModels.EventStatus.PENDING) {
				JButton retry = actionButton("Opnieuw proberen", true);
				retry.addActionListener(e -> runSubmissionAction(() -> pipeline.retryGroup(record)));
				actionButtons.add(retry);
			}
			if ((displayStatus == ZweetnekkenModels.EventStatus.DRAFT
					|| displayStatus == ZweetnekkenModels.EventStatus.FAILED) && canFinalize(group)) {
				JButton submit = actionButton("Inzenden", true);
				submit.setToolTipText("Bevestig de deelnemers en stuur de inzending naar het gebruikelijke /loot-kanaal op Discord.");
				submit.addActionListener(e -> {
					int partySize = (Integer) party.getValue();
					int clanCount = (Integer) clanMembers.getValue();
					int nonClanCount = (Integer) nonClanMembers.getValue();
					if (clanCount + nonClanCount > partySize) {
						JOptionPane.showMessageDialog(this, "Het aantal clanleden en gastspelers kan niet groter zijn dan de groepsgrootte.");
						return;
					}
					String submissionNotes = notes.getText();
					List<ZweetnekkenModels.PartyMember> partyMembers = partyMemberEditor == null ? List.of()
							: partyMemberEditor.members();
					runSubmissionAction(() -> pipeline.updateAndSubmitGroup(record, partySize, clanCount, nonClanCount,
							partyMembers, submissionNotes));
				});
				actionButtons.add(submit);
			}
			JButton discard = actionButton("Verwijderen", false);
			discard.addActionListener(e -> {
			if (JOptionPane.showConfirmDialog(this, "Dit lokale bewijs verwijderen?", "Bewijs verwijderen",
						JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
					runSubmissionAction(() -> {
						for (EvidenceStore.Record member : group) evidence.deleteLocal(member.metadata.eventId);
					});
				}
			});
			actionButtons.add(discard);
			JPanel actions = actionGrid(actionButtons);
			card.add(Box.createVerticalStrut(5));
			card.add(actions);
			submissions.add(card);
			submissions.add(Box.createVerticalStrut(7));
		}
	}

	private void runSubmissionAction(Runnable action) {
		new SwingWorker<Void, Void>() {
			@Override protected Void doInBackground() {
				action.run();
				return null;
			}

			@Override protected void done() {
				try {
					get();
				} catch (InterruptedException | java.util.concurrent.ExecutionException e) {
				JOptionPane.showMessageDialog(ZweetnekkenPanel.this, "De lokale inzending kon niet worden bijgewerkt.");
				}
				refresh();
			}
		}.execute();
	}

	private static java.util.Map<String, List<EvidenceStore.Record>> submissionGroups(List<EvidenceStore.Record> records) {
		java.util.Map<String, List<EvidenceStore.Record>> groups = new java.util.LinkedHashMap<>();
		for (EvidenceStore.Record record : records) {
			String groupId = EventPipeline.submissionGroupId(record);
			String key = groupId == null ? "event:" + record.metadata.eventId : "group:" + groupId;
			groups.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(record);
		}
		return groups;
	}

	private static EvidenceStore.Record preferredRecord(List<EvidenceStore.Record> group) {
		EvidenceStore.Record preferred = group.get(0);
		for (EvidenceStore.Record record : group) {
			if (eventPriority(record) < eventPriority(preferred)) preferred = record;
		}
		return preferred;
	}

	private static int eventPriority(EvidenceStore.Record record) {
		String type = record == null || record.metadata == null ? null : record.metadata.eventType;
		if ("bingo".equals(type)) return 0;
		if ("loot".equals(type)) return 1;
		if ("personal_best".equals(type)) return 2;
		return 3;
	}

	private static ZweetnekkenModels.EventStatus groupStatus(List<EvidenceStore.Record> group) {
		if (group == null || group.isEmpty()) return ZweetnekkenModels.EventStatus.PENDING;
		for (ZweetnekkenModels.EventStatus candidate : new ZweetnekkenModels.EventStatus[] {
			ZweetnekkenModels.EventStatus.FAILED, ZweetnekkenModels.EventStatus.PENDING, ZweetnekkenModels.EventStatus.UPLOADING,
			ZweetnekkenModels.EventStatus.DRAFT, ZweetnekkenModels.EventStatus.REJECTED, ZweetnekkenModels.EventStatus.SUBMITTED,
			ZweetnekkenModels.EventStatus.APPROVED }) {
			for (EvidenceStore.Record record : group) if (record.status == candidate) return candidate;
		}
		return group.get(0).status;
	}

	private boolean canFinalize(List<EvidenceStore.Record> group) {
		if (group == null || !config.autoSubmitEnabled()) return true;
		for (EvidenceStore.Record record : group) {
			if (record == null || record.metadata == null || record.metadata.context == null) continue;
			// A locally-created draft can explicitly opt out of automatic
			// submission while still being intended for manual submission.
			if (record.metadata.details != null
					&& Boolean.FALSE.equals(record.metadata.details.get("autoSubmit"))) continue;
			Object value = record.metadata.context.get("finalizeSubmission");
			if (Boolean.FALSE.equals(value) || "false".equalsIgnoreCase(String.valueOf(value))) return false;
		}
		return true;
	}

	private static EvidenceStore.Record proofRecord(List<EvidenceStore.Record> group, EvidenceStore.Record fallback) {
		if (fallback != null && fallback.screenshotPath != null && !fallback.screenshotPath.isBlank()) return fallback;
		if (group != null)
			for (EvidenceStore.Record record : group)
				if (record != null && record.screenshotPath != null && !record.screenshotPath.isBlank()) return record;
		return null;
	}

	private static String partyMemberNames(ZweetnekkenModels.Party party) {
		List<ZweetnekkenModels.PartyMember> members = party == null ? List.of()
				: party.submittedMembers == null || party.submittedMembers.isEmpty() ? party.members : party.submittedMembers;
		if (members.isEmpty())
			return "";
		StringBuilder names = new StringBuilder("Groep: ");
		for (ZweetnekkenModels.PartyMember member : members) {
			if (member == null || member.name == null || member.name.isBlank())
				continue;
			if (names.length() > 7)
				names.append(", ");
			names.append(member.name);
			if (member.clanMember != null)
				names.append(Boolean.TRUE.equals(member.clanMember) ? " [clan]" : " [guest]");
		}
		return names.length() == 7 ? "" : names.toString();
	}

	private final class PartyMemberEditor {
		private final List<ZweetnekkenModels.PartyMember> members = new java.util.ArrayList<>();

		PartyMemberEditor(ZweetnekkenModels.Party party) {
			List<ZweetnekkenModels.PartyMember> source = party.submittedMembers == null || party.submittedMembers.isEmpty()
					? party.members : party.submittedMembers;
			for (ZweetnekkenModels.PartyMember member : source) members.add(copyPartyMember(member));
		}

		void show(JSpinner partySize, JSpinner clanMembers, JSpinner nonClanMembers) {
			JSpinner size = new JSpinner(new SpinnerNumberModel(((Integer) partySize.getValue()).intValue(), 1, 100, 1));
			JPanel roster = verticalPanel();
			size.addChangeListener(e -> rebuildRoster(roster, (Integer) size.getValue()));
			rebuildRoster(roster, (Integer) size.getValue());
			JPanel content = verticalPanel();
			content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
			content.add(fieldRow("Groepsgrootte", size));
			content.add(Box.createVerticalStrut(5));
			content.add(label("Leden", false));
			JScrollPane scroll = scroll(roster);
			scroll.setPreferredSize(new Dimension(360, 190));
			content.add(scroll);
			int choice = JOptionPane.showConfirmDialog(ZweetnekkenPanel.this, content, "Groepsleden aanpassen",
					JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
			if (choice != JOptionPane.OK_OPTION) return;
			readRoster(roster);
			partySize.setValue(size.getValue());
			int clan = 0, nonClan = 0;
			for (ZweetnekkenModels.PartyMember member : members) {
				if (Boolean.TRUE.equals(member.clanMember)) clan++;
				else if (Boolean.FALSE.equals(member.clanMember)) nonClan++;
			}
			clanMembers.setValue(clan);
			nonClanMembers.setValue(nonClan);
		}

		List<ZweetnekkenModels.PartyMember> members() {
			List<ZweetnekkenModels.PartyMember> copy = new java.util.ArrayList<>();
			for (ZweetnekkenModels.PartyMember member : members) copy.add(copyPartyMember(member));
			return copy;
		}

		private void rebuildRoster(JPanel roster, int count) {
			readRoster(roster);
			while (members.size() < count) members.add(new ZweetnekkenModels.PartyMember());
			while (members.size() > count) members.remove(members.size() - 1);
			roster.removeAll();
			for (int index = 0; index < members.size(); index++) {
				ZweetnekkenModels.PartyMember member = members.get(index);
				JPanel row = new JPanel(new BorderLayout(5, 0));
				row.setOpaque(false);
				JTextField name = new JTextField(member.name == null ? "" : member.name);
				name.putClientProperty("partyIndex", index);
				name.setToolTipText("RuneScape-naam");
				JComboBox<String> role = new JComboBox<>(new String[] { "Onbekend", "Clan", "Gast" });
				role.setSelectedItem(Boolean.TRUE.equals(member.clanMember) ? "Clan"
						: Boolean.FALSE.equals(member.clanMember) ? "Gast" : "Onbekend");
				role.putClientProperty("partyIndex", index);
				row.add(new JLabel((index + 1) + "."), BorderLayout.WEST);
				row.add(name, BorderLayout.CENTER);
				row.add(role, BorderLayout.EAST);
				roster.add(row);
				if (index < members.size() - 1) roster.add(Box.createVerticalStrut(3));
			}
			roster.revalidate(); roster.repaint();
		}

		private void readRoster(JPanel roster) {
			for (Component component : roster.getComponents()) {
				if (!(component instanceof JPanel)) continue;
				for (Component field : ((JPanel) component).getComponents()) {
					Object index = field instanceof javax.swing.JComponent
							? ((javax.swing.JComponent) field).getClientProperty("partyIndex") : null;
					if (!(index instanceof Integer) || (Integer) index >= members.size()) continue;
					ZweetnekkenModels.PartyMember member = members.get((Integer) index);
					if (field instanceof JTextField) member.name = ((JTextField) field).getText().trim();
					if (field instanceof JComboBox) {
						String role = String.valueOf(((JComboBox<?>) field).getSelectedItem());
						member.clanMember = "Clan".equals(role) ? Boolean.TRUE : "Gast".equals(role) ? Boolean.FALSE : null;
					}
				}
			}
		}

		private ZweetnekkenModels.PartyMember copyPartyMember(ZweetnekkenModels.PartyMember source) {
			ZweetnekkenModels.PartyMember copy = new ZweetnekkenModels.PartyMember();
			if (source != null) { copy.name = source.name; copy.clanMember = source.clanMember; }
			return copy;
		}
	}

	private JPanel submissionHeading(EvidenceStore.Record record, ZweetnekkenModels.EventStatus displayStatus,
			List<EvidenceStore.Record> group) {
		JPanel heading = new JPanel(new BorderLayout(7, 0));
		heading.setOpaque(false);
		heading.setAlignmentX(Component.LEFT_ALIGNMENT);
		JPanel text = verticalPanel();
		text.setOpaque(false);
		text.add(sectionHeading(eventLabel(record, group)));
		text.add(Box.createVerticalStrut(2));
		JLabel title = new JLabel(title(record));
		title.setForeground(Color.WHITE);
		title.setFont(ZweetnekkenTheme.font(true));
		title.setToolTipText(title.getText());
		text.add(title);
		heading.add(text, BorderLayout.CENTER);
		heading.add(statusBadge(displayStatus), BorderLayout.EAST);
		Integer itemId = submissionItemId(record);
		String activity = pbActivity(record);
		if (itemId != null) {
			JLabel icon = new JLabel();
			icon.setPreferredSize(new Dimension(30, 30));
			icon.setHorizontalAlignment(SwingConstants.CENTER);
			AsyncBufferedImage image = itemManager.getImage(itemId);
			Runnable update = () -> {
				icon.setIcon(new ImageIcon(scale(image, 28, 28)));
				icon.repaint();
			};
			update.run();
			image.onLoaded(() -> SwingUtilities.invokeLater(update));
			heading.add(icon, BorderLayout.WEST);
		} else if (activity != null) {
			JLabel icon = new JLabel();
			icon.setPreferredSize(new Dimension(30, 30));
			icon.setHorizontalAlignment(SwingConstants.CENTER);
			BossIcons.get(spriteManager, activity, loaded -> {
				icon.setIcon(loaded);
				icon.repaint();
			});
			heading.add(icon, BorderLayout.WEST);
		}
		heading.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(32, heading.getPreferredSize().height)));
		return heading;
	}

	private static JPanel actionGrid(List<JButton> buttons) {
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setOpaque(false);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (int i = 0; i < buttons.size(); i++) {
			boolean fullWidthLast = buttons.size() % 2 == 1 && i == buttons.size() - 1;
			GridBagConstraints constraints = new GridBagConstraints();
			constraints.gridx = fullWidthLast ? 0 : i % 2;
			constraints.gridy = i / 2;
			constraints.gridwidth = fullWidthLast ? 2 : 1;
			constraints.weightx = fullWidthLast ? 2.0 : 1.0;
			constraints.fill = GridBagConstraints.HORIZONTAL;
			constraints.insets = fullWidthLast ? new Insets(i >= 2 ? 4 : 0, 0, 0, 0)
					: new Insets(i >= 2 ? 4 : 0, i % 2 == 1 ? 3 : 0, 0, i % 2 == 0 ? 3 : 0);
			panel.add(buttons.get(i), constraints);
		}
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
		return panel;
	}

	static JButton actionButton(String text, boolean primary) {
		JButton button = new JButton(text);
		button.setForeground(primary ? GOLD : TEXT_MUTED);
		button.setFont(ZweetnekkenTheme.font(true));
		button.setFocusPainted(false);
		button.setBorderPainted(false);
		button.setContentAreaFilled(false);
		button.setOpaque(false);
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		button.setMargin(new Insets(0, 8, 0, 8));
		button.setPreferredSize(new Dimension(0, 28));
		button.setBackground(PRIMARY_BUTTON_BG);
		button.setRolloverEnabled(true);
		button.setUI(new javax.swing.plaf.basic.BasicButtonUI() {
			@Override
			public void paint(Graphics graphics, javax.swing.JComponent component) {
				JButton target = (JButton) component;
				Graphics2D g = (Graphics2D) graphics.create();
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				Color background = target.getBackground();
				if (target.getModel().isPressed())
					background = background.darker();
				else if (target.getModel().isRollover())
					background = TAB_HOVER_BG;
				g.setColor(background);
				g.fillRoundRect(0, 0, target.getWidth(), target.getHeight(), 6, 6);
				Color accent = primary ? GOLD : TEXT_MUTED;
				g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 180));
				g.drawRoundRect(0, 0, target.getWidth() - 1, target.getHeight() - 1, 6, 6);
				g.dispose();
				super.paint(graphics, component);
			}
		});
		return button;
	}

	static JButton sectionToggleButton(String title, boolean expanded) {
		JButton toggle = actionButton((expanded ? "▾ " : "▸ ") + title, false);
		// Keep all collapsible section headings in RuneScape's bold pixel font.
		toggle.setFont(ZweetnekkenTheme.font(true));
		toggle.setHorizontalAlignment(SwingConstants.LEFT);
		toggle.setToolTipText(expanded ? title + " inklappen" : title + " uitklappen");
		toggle.setAlignmentX(Component.LEFT_ALIGNMENT);
		toggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, NAV_BUTTON_HEIGHT));
		return toggle;
	}

	private static JLabel statusBadge(ZweetnekkenModels.EventStatus status) {
		String text = status == null ? "ONBEKEND" : statusLabel(status);
		Color color = status == ZweetnekkenModels.EventStatus.SUBMITTED || status == ZweetnekkenModels.EventStatus.APPROVED
				? CONNECTED_COLOR
				: status == ZweetnekkenModels.EventStatus.FAILED || status == ZweetnekkenModels.EventStatus.REJECTED
						? new Color(194, 83, 80)
						: status == ZweetnekkenModels.EventStatus.DRAFT ? GOLD : new Color(150, 154, 158);
		JLabel badge = new JLabel(text, SwingConstants.CENTER);
		badge.setForeground(color);
		badge.setFont(ZweetnekkenTheme.font(true));
		badge.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(color.getRed(), color.getGreen(), color.getBlue(), 135)),
				BorderFactory.createEmptyBorder(3, 6, 3, 6)));
		badge.setAlignmentY(Component.CENTER_ALIGNMENT);
		return badge;
	}

	private static String statusLabel(ZweetnekkenModels.EventStatus status) {
		switch (status) {
			case PENDING: return "IN WACHTRIJ";
			case UPLOADING: return "UPLOADEN";
			case DRAFT: return "CONCEPT";
			case SUBMITTED: return "INGEDIEND";
			case APPROVED: return "GOEDGEKEURD";
			case REJECTED: return "AFGEWEZEN";
			case FAILED: return "MISLUKT";
			default: return "ONBEKEND";
		}
	}

	private static JPanel fieldRow(String name, Component field) {
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel label = new JLabel(name);
		label.setForeground(Color.WHITE);
		row.add(label, BorderLayout.CENTER);
		row.add(field, BorderLayout.EAST);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	private void showProof(EvidenceStore.Record record) {
		try {
			var screenshot = evidence.resolveScreenshot(record.screenshotPath);
			if (screenshot == null) throw new IllegalArgumentException("Evidence file is unavailable");
			BufferedImage image;
			try (var input = screenshot.openInputStream()) {
				image = ImageIO.read(input);
			}
			if (image == null)
				throw new IllegalArgumentException("Unsupported evidence image");
			Window owner = SwingUtilities.getWindowAncestor(this);
			JDialog dialog = new JDialog(owner, "Bewijs", Dialog.ModalityType.APPLICATION_MODAL);
			dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
			dialog.setResizable(true);
			dialog.setMinimumSize(new Dimension(360, 260));
			dialog.add(new EvidenceImagePanel(image), BorderLayout.CENTER);
			Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
			dialog.setSize(Math.min(900, screen.width - 80), Math.min(700, screen.height - 80));
			dialog.setLocationRelativeTo(this);
			dialog.setVisible(true);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(this, "Het bewijsbestand is niet beschikbaar.");
		}
	}

	static JPanel verticalPanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(PANEL_BACKGROUND);
		return panel;
	}

	private static JPanel fullWidthColumn() {
		JPanel panel = new JPanel(new FullWidthColumnLayout());
		panel.setBackground(PANEL_BACKGROUND);
		return panel;
	}

	/** Keeps each profile row flush with the available content column. */
	private static final class FullWidthColumnLayout implements LayoutManager {
		@Override public void addLayoutComponent(String name, Component component) { }
		@Override public void removeLayoutComponent(Component component) { }

		@Override
		public Dimension preferredLayoutSize(java.awt.Container parent) {
			synchronized (parent.getTreeLock()) {
				Insets insets = parent.getInsets();
				int width = 0;
				int height = 0;
				for (Component child : parent.getComponents()) {
					if (!child.isVisible()) continue;
					Dimension preferred = child.getPreferredSize();
					width = Math.max(width, preferred.width);
					height += preferred.height;
				}
				return new Dimension(insets.left + width + insets.right,
						insets.top + height + insets.bottom);
			}
		}

		@Override
		public Dimension minimumLayoutSize(java.awt.Container parent) {
			return preferredLayoutSize(parent);
		}

		@Override
		public void layoutContainer(java.awt.Container parent) {
			synchronized (parent.getTreeLock()) {
				Insets insets = parent.getInsets();
				int width = Math.max(0, parent.getWidth() - insets.left - insets.right);
				int y = insets.top;
				for (Component child : parent.getComponents()) {
					if (!child.isVisible()) continue;
					int height = child.getPreferredSize().height;
					child.setBounds(insets.left, y, width, height);
					y += height;
				}
			}
		}
	}

	private static JPanel fullWidthVerticalPanel() {
		JPanel panel = new JPanel() {
			@Override
			public Dimension getMaximumSize() {
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(PANEL_BACKGROUND);
		return panel;
	}

	static JPanel blackDivider(int height) {
		JPanel divider = new JPanel();
		divider.setOpaque(true);
		divider.setBackground(PANEL_BACKGROUND);
		divider.setPreferredSize(new Dimension(1, height));
		divider.setMinimumSize(new Dimension(1, height));
		divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		divider.setAlignmentX(Component.LEFT_ALIGNMENT);
		return divider;
	}

	static JPanel card() {
		return ZweetnekkenTheme.surface();
	}

	static JScrollPane scroll(JPanel panel) {
		return scroll(panel, false);
	}

	static JScrollPane scroll(JPanel panel, boolean reserveScrollbarSpace) {
		ViewportWidthPanel viewport = new ViewportWidthPanel();
		viewport.setBorder(BorderFactory.createEmptyBorder(0, 6, 8, 6));
		viewport.add(panel, BorderLayout.NORTH);
		JScrollPane scroll = new JScrollPane(viewport);
		scroll.setBorder(null);
		scroll.setBackground(PANEL_BACKGROUND);
		scroll.getViewport().setBackground(PANEL_BACKGROUND);
		scroll.getViewport().setOpaque(true);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		// Keep the sidebar width stable when the settings actions expand or collapse.
		scroll.setVerticalScrollBarPolicy(reserveScrollbarSpace
				? JScrollPane.VERTICAL_SCROLLBAR_ALWAYS : JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.getVerticalScrollBar().setPreferredSize(new Dimension(6, 0));
		return scroll;
	}

	private static final class ViewportWidthPanel extends JPanel implements Scrollable {
		private ViewportWidthPanel() {
			super(new BorderLayout());
			setBackground(PANEL_BACKGROUND);
		}

		@Override
		public Dimension getPreferredScrollableViewportSize() {
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
			return Math.max(16, visible.height - 16);
		}

		@Override
		public boolean getScrollableTracksViewportWidth() {
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight() {
			// Preserve the cards' natural height instead of switching between fitting and scrolling.
			return false;
		}
	}

	private static JLabel label(String text, boolean bold) {
		JLabel label = new JLabel(html(esc(text)));
		label.setForeground(Color.WHITE);
		label.setFont((bold ? net.runelite.client.ui.FontManager.getRunescapeBoldFont()
				: net.runelite.client.ui.FontManager.getRunescapeSmallFont()));
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
		return label;
	}

	static JLabel muted(String text) {
		JLabel label = new JLabel(html("<span style='color:#96968c'>" + esc(text) + "</span>"));
		label.setForeground(TEXT_MUTED);
		label.setFont(ZweetnekkenTheme.font(false));
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
		return label;
	}

	private static JLabel wrappedMuted(String text) {
		JLabel label = muted(text);
		label.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		return label;
	}

	private static JPanel row(String leftLabel, String leftValue, String rightLabel, String rightValue) {
		JPanel p = new JPanel(new java.awt.GridLayout(1, 2, 4, 0));
		p.setOpaque(false);
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		JLabel left = new JLabel(leftLabel + " " + leftValue);
		left.setForeground(Color.WHITE);
		JLabel right = new JLabel((rightLabel + " " + rightValue).trim());
		right.setForeground(Color.WHITE);
		p.add(left);
		p.add(right);
		return p;
	}

	static JLabel sectionHeading(String text) {
		JLabel heading = new JLabel(text);
		heading.setForeground(new Color(155, 158, 161));
		heading.setFont(ZweetnekkenTheme.font(true));
		heading.setAlignmentX(Component.LEFT_ALIGNMENT);
		return heading;
	}

	private static JLabel competitionSectionHeading(String text, Color color) {
		JLabel heading = new JLabel(text);
		heading.setForeground(color);
		heading.setFont(ZweetnekkenTheme.font(true));
		heading.setAlignmentX(Component.LEFT_ALIGNMENT);
		return heading;
	}

	private static JPanel statRow(String leftLabel, String leftValue, Color leftColor,
			String rightLabel, String rightValue, Color rightColor) {
		JPanel row = new JPanel(new GridLayout(1, 2, 8, 0));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
		row.add(stat(leftLabel, leftValue, leftColor));
		row.add(stat(rightLabel, rightValue, rightColor));
		return row;
	}

	private static JPanel stat(String label, String value, Color color) {
		JPanel stat = new JPanel(new BorderLayout(0, 1));
		stat.setOpaque(false);
		JLabel name = new JLabel(label);
		name.setForeground(TEXT_MUTED);
		name.setFont(ZweetnekkenTheme.font(false));
		JLabel amount = new JLabel(value);
		amount.setForeground(color);
		amount.setFont(ZweetnekkenTheme.font(true));
		stat.add(name, BorderLayout.NORTH);
		stat.add(amount, BorderLayout.CENTER);
		return stat;
	}

	private static JPanel nextRank(String rankName) {
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
		JLabel label = new JLabel("VOLGENDE · " + rankName, SwingConstants.LEFT);
		label.setFont(ZweetnekkenTheme.font(true));
		label.setForeground(Color.WHITE);
		row.add(label, BorderLayout.WEST);
		return row;
	}

	private static JPanel weeklyTimer(String timing) {
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBackground(CARD_BACKGROUND);
		p.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 3, 0, 0, GOLD),
				BorderFactory.createEmptyBorder(6, 8, 6, 8)));
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
		JLabel title = new JLabel("WEKELIJKSE EVENTS");
		title.setForeground(new Color(155, 158, 161));
		title.setFont(ZweetnekkenTheme.font(true));
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel value = new JLabel(timing.toUpperCase(Locale.ROOT));
		value.setForeground(SCORE_COLOR);
		value.setFont(ZweetnekkenTheme.font(true));
		value.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.add(title);
		p.add(Box.createVerticalStrut(2));
		p.add(value);
		return p;
	}

	private static JPanel leaderRow(int rank, String name, String score, double progress, Color accent,
			boolean highlighted) {
		return leaderRow(rank, name, score, progress, accent, highlighted, 0);
	}

	private static JPanel leaderRow(int rank, String name, String score, double progress, Color accent,
			boolean highlighted, int minimumScoreWidth) {
		JPanel p = new JPanel(new BorderLayout(6, 0)) {
			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(highlighted ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 38)
						: new Color(255, 255, 255, 10));
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
				int barWidth = (int) Math.round(Math.max(0, Math.min(1, progress)) * getWidth());
				g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 72));
				g2.fillRoundRect(0, 0, barWidth, getHeight(), 6, 6);
				g2.dispose();
			}
		};
		p.setOpaque(false);
		p.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		p.setPreferredSize(new Dimension(0, 28));

		JLabel rankLabel = new JLabel(String.valueOf(rank), SwingConstants.CENTER);
		rankLabel.setForeground(rank <= RANK_COLORS.length
				? RANK_COLORS[Math.max(0, rank - 1)]
				: highlighted ? accent : new Color(160, 163, 166));
		rankLabel.setFont(ZweetnekkenTheme.font(true));
		rankLabel.setPreferredSize(new Dimension(18, 18));
		JLabel nameLabel = new JLabel(name);
		nameLabel.setForeground(new Color(225, 225, 225));
		nameLabel.setFont(ZweetnekkenTheme.font(true));
		nameLabel.setToolTipText(name);
		JLabel scoreLabel = new JLabel(styledScore(score), SwingConstants.RIGHT);
		scoreLabel.setForeground(SCORE_COLOR);
		scoreLabel.setFont(ZweetnekkenTheme.font(true));
		scoreLabel.setToolTipText(score);
		int scoreWidth = Math.max(minimumScoreWidth, Math.min(90, scoreLabel.getPreferredSize().width));
		scoreLabel.setPreferredSize(new Dimension(scoreWidth, 18));
		if (minimumScoreWidth > 0) scoreLabel.setMinimumSize(new Dimension(scoreWidth, 18));
		p.add(rankLabel, BorderLayout.WEST);
		p.add(nameLabel, BorderLayout.CENTER);
		p.add(scoreLabel, BorderLayout.EAST);
		return p;
	}

	static JPanel eventLeaderRow(int rank, String name, String score, double progress) {
		return leaderRow(rank, name, score, progress, BOTW_ACCENT, false, 76);
	}

	private static String styledScore(String score) {
		String escaped = esc(score);
		int split = escaped.lastIndexOf(' ');
		if (split < 0)
			return escaped;
		return "<html>" + escaped.substring(0, split) + " <span style='color:#96968c'>"
				+ escaped.substring(split + 1) + "</span></html>";
	}

	private static String sharedCompetitionTiming(ZweetnekkenModels.CompetitionPanels competitions) {
		if (competitions == null)
			return "";
		boolean botwUpcoming = competitions.botw != null && isUpcoming(competitions.botw);
		boolean sotwUpcoming = competitions.sotw != null && isUpcoming(competitions.sotw);
		if (botwUpcoming && sotwUpcoming) {
			String botwStart = competitions.botw.startsAt;
			String sotwStart = competitions.sotw.startsAt;
			return startsCountdown(botwStart == null || botwStart.isBlank() ? sotwStart : botwStart);
		}
		boolean botwFinished = competitions.botw != null && isFinished(competitions.botw);
		boolean sotwFinished = competitions.sotw != null && isFinished(competitions.sotw);
		if (botwFinished && sotwFinished) {
			return "Competities afgelopen";
		}
		String botwEnd = competitions.botw == null ? null : competitions.botw.endsAt;
		String sotwEnd = competitions.sotw == null ? null : competitions.sotw.endsAt;
		return countdown(botwEnd == null || botwEnd.isBlank() ? sotwEnd : botwEnd);
	}

	private static JPanel progress(String name, long current, long remaining, Color accent) {
		return progress(name, current, remaining, accent, true);
	}

	private static JPanel progress(String name, long current, long remaining, Color accent, boolean showRemaining) {
		JPanel p = verticalPanel();
		p.setOpaque(false);
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, showRemaining ? 44 : 26));
		long target = Math.max(1, current + remaining);
		JPanel caption = new JPanel(new BorderLayout(5, 0));
		caption.setOpaque(false);
		caption.setAlignmentX(Component.LEFT_ALIGNMENT);
		caption.setMaximumSize(new Dimension(Integer.MAX_VALUE, 15));
		JLabel metric = new JLabel(name + "-punten");
		metric.setForeground(TEXT_MUTED);
		metric.setFont(ZweetnekkenTheme.font(false));
		JLabel value = new JLabel(number(current) + " / " + number(target), SwingConstants.RIGHT);
		value.setForeground(accent);
		value.setFont(ZweetnekkenTheme.font(true));
		caption.add(metric, BorderLayout.WEST);
		caption.add(value, BorderLayout.EAST);
		p.add(caption);
		JProgressBar bar = new JProgressBar(0, 1000);
		bar.setValue((int) Math.min(1000, current * 1000 / target));
		bar.setStringPainted(false);
		bar.setForeground(accent);
		bar.setBackground(new Color(52, 55, 58));
		bar.setBorder(BorderFactory.createEmptyBorder());
		bar.setPreferredSize(new Dimension(100, 7));
		bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 7));
		bar.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.add(Box.createVerticalStrut(3));
		p.add(bar);
		if (showRemaining) {
			JLabel remainingLabel = muted(remaining <= 0 ? "Vereiste behaald" : number(remaining) + " resterend");
			remainingLabel.setFont(ZweetnekkenTheme.font(false));
			p.add(Box.createVerticalStrut(2));
			p.add(remainingLabel);
		}
		return p;
	}

	private static String eventLabel(EvidenceStore.Record record, List<EvidenceStore.Record> group) {
		String type = record.metadata == null ? null : record.metadata.eventType;
		if (type == null)
			return "INZENDING";
		java.util.Set<String> groupedTypes = new java.util.HashSet<>();
		if (group != null)
			for (EvidenceStore.Record member : group)
				if (member != null && member.metadata != null && member.metadata.eventType != null)
					groupedTypes.add(member.metadata.eventType);
		if (record.metadata.context != null) {
			Object values = record.metadata.context.get("submissionTypes");
			if (values instanceof Iterable)
				for (Object value : (Iterable<?>) values) if (value != null) groupedTypes.add(String.valueOf(value));
		}
		if (groupedTypes.size() > 1) {
			List<String> labels = new java.util.ArrayList<>();
			if (groupedTypes.contains("bingo")) labels.add("BINGO");
			if (groupedTypes.contains("loot")) labels.add("LOOT");
			if (groupedTypes.contains("personal_best")) labels.add("PB");
			for (String groupedType : groupedTypes)
				if (!"bingo".equals(groupedType) && !"loot".equals(groupedType) && !"personal_best".equals(groupedType))
					labels.add(groupedType.replace('_', ' ').toUpperCase(Locale.ROOT));
			return String.join("+", labels);
		}
		switch (type) {
			case "bingo":
				return "BINGO";
			case "loot":
				return "LOOT DROP";
			case "personal_best":
				return "PB";
			case "collection_log":
				return "COLLECTION LOG";
			case "pet":
				return "PET";
			case "combat_achievement_tier":
				return "COMBAT ACHIEVEMENT";
			default:
				return type.replace('_', ' ').toUpperCase(Locale.ROOT);
		}
	}

	private static boolean isPersonalBest(EvidenceStore.Record record) {
		return record != null && record.metadata != null && "personal_best".equals(record.metadata.eventType);
	}

	private static String friendlyMethod(String method) {
		if (method == null || method.isBlank() || "unknown".equalsIgnoreCase(method))
			return "gameactiviteit";
		return method.replace('_', ' ');
	}

	private static String pbActivity(EvidenceStore.Record record) {
		if (!isPersonalBest(record)
				|| record.metadata.details == null)
			return null;
		Object pb = record.metadata.details.get("record");
		if (pb instanceof ZweetnekkenModels.PbRecord)
			return value(((ZweetnekkenModels.PbRecord) pb).activity);
		if (pb instanceof java.util.Map) {
			Object activity = ((java.util.Map<?, ?>) pb).get("activity");
			if (activity != null && !String.valueOf(activity).isBlank())
				return String.valueOf(activity);
		}
		return null;
	}

	static Integer submissionItemId(EvidenceStore.Record record) {
		if (record == null || record.metadata == null || record.metadata.items == null
				|| record.metadata.items.isEmpty())
			return null;
		int itemId = record.metadata.items.get(0).itemId;
		return itemId > 0 ? itemId : null;
	}

	private static String title(EvidenceStore.Record record) {
		if (record.metadata == null)
			return "Inzending";
		if (record.metadata.items != null && !record.metadata.items.isEmpty())
			return value(record.metadata.items.get(0).name);
		String detailTitle = firstDetail(record, "itemName", "petName", "taskName");
		if (detailTitle != null)
			return detailTitle;
		String activity = pbActivity(record);
		if (activity != null)
			return titleCase(activity);
		String type = record.metadata.eventType == null ? "submission" : record.metadata.eventType.replace('_', ' ');
		return capitalize(type);
	}

	private static String firstDetail(EvidenceStore.Record record, String... keys) {
		if (record.metadata.details == null)
			return null;
		for (String key : keys) {
			Object value = record.metadata.details.get(key);
			if (value != null && !String.valueOf(value).isBlank())
				return String.valueOf(value);
		}
		return null;
	}

	private static String rank(Integer value) {
		return value == null ? "—" : "#" + value;
	}

	private static String number(long value) {
		return NumberFormat.getIntegerInstance(Locale.US).format(value);
	}

	private static String value(String value) {
		return value == null || value.isBlank() ? "—" : value;
	}

	private static boolean isFinished(ZweetnekkenModels.CompetitionPanel competition) {
		if (competition == null)
			return false;
		if ("finished".equalsIgnoreCase(competition.status)
				|| "ended".equalsIgnoreCase(competition.status)
				|| "completed".equalsIgnoreCase(competition.status)
				|| "inactive".equalsIgnoreCase(competition.status)) {
			return true;
		}
		if (competition.endsAt != null && !competition.endsAt.isBlank()) {
			try {
				return Instant.parse(competition.endsAt).isBefore(Instant.now());
			} catch (RuntimeException ignored) {
			}
		}
		return false;
	}

	private static boolean isUpcoming(ZweetnekkenModels.CompetitionPanel competition) {
		if (competition == null)
			return false;
		if ("upcoming".equalsIgnoreCase(competition.status))
			return true;
		if (competition.startsAt != null && !competition.startsAt.isBlank()) {
			try {
				return Instant.parse(competition.startsAt).isAfter(Instant.now());
			} catch (RuntimeException ignored) {
			}
		}
		return false;
	}

	private static String startsCountdown(String start) {
		try {
			Duration d = Duration.between(Instant.now(), Instant.parse(start));
			if (d.isNegative())
				return "Start nu";
			long days = d.toDays();
			long hours = d.minusDays(days).toHours();
			long minutes = d.minusDays(days).minusHours(hours).toMinutes();
			return "Start over " + (days > 0 ? days + "d " : "") + (hours > 0 ? hours + "u " : "") + minutes + "m";
		} catch (RuntimeException e) {
			return "Start binnenkort";
		}
	}

	private static String countdown(String end) {
		try {
			Duration d = Duration.between(Instant.now(), Instant.parse(end));
			if (d.isNegative())
				return "Afgelopen";
			return "Eindigt over " + d.toDays() + "d " + d.minusDays(d.toDays()).toHours() + "u";
		} catch (RuntimeException e) {
			return "";
		}
	}

	private static String capitalize(String value) {
		return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
	}

	private static String titleCase(String value) {
		if (value == null || value.isBlank())
			return value;
		String[] words = value.toLowerCase(Locale.ROOT).split("\\s+");
		StringBuilder result = new StringBuilder(value.length());
		for (String word : words) {
			if (result.length() > 0)
				result.append(' ');
			result.append(capitalize(word));
		}
		return result.toString();
	}

	private static String html(String value) {
		return "<html><div style='width:" + (PANEL_WIDTH - 60) + "px'>" + value + "</div></html>";
	}

	private static String esc(String value) {
		return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static BufferedImage scale(BufferedImage image, int maxWidth, int maxHeight) {
		double factor = Math.min((double) maxWidth / image.getWidth(), (double) maxHeight / image.getHeight());
		factor = Math.min(1.0, factor);
		int width = Math.max(1, (int) (image.getWidth() * factor));
		int height = Math.max(1, (int) (image.getHeight() * factor));
		BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = scaled.createGraphics();
		g.drawImage(image, 0, 0, width, height, null);
		g.dispose();
		return scaled;
	}

	private static BufferedImage scaleToWidth(BufferedImage image, int width) {
		double factor = (double) width / image.getWidth();
		int height = Math.max(1, (int) Math.round(image.getHeight() * factor));
		BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = scaled.createGraphics();
		g.drawImage(image, 0, 0, width, height, null);
		g.dispose();
		return scaled;
	}

	private static BufferedImage placeholderBanner() {
		BufferedImage i = new BufferedImage(PANEL_WIDTH, 62, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = i.createGraphics();
		g.setColor(new Color(22, 31, 43));
		g.fillRect(0, 0, i.getWidth(), i.getHeight());
		g.setColor(GOLD);
		g.setFont(new Font("SansSerif", Font.BOLD, 24));
		g.drawString("ZWEETNEKKEN", 38, 39);
		g.dispose();
		return i;
	}

	private static BufferedImage fallbackBanner() {
		// The fallback is generated locally, so a legacy packaged image can never be shown.
		return placeholderBanner();
	}

	private static final class AspectRatioIconLabel extends JLabel {
		private int aspectHeight = -1;

		private AspectRatioIconLabel() {
			setOpaque(true);
			setBackground(PANEL_BACKGROUND);
			addComponentListener(new ComponentAdapter() {
				@Override
				public void componentResized(ComponentEvent event) {
					refreshAspectRatio();
				}
			});
		}

		/**
		 * Keep the icon's native pixel width from becoming the sidebar's preferred
		 * width. Remote banners can be 960+ pixels wide; using that intrinsic size
		 * here can make RuneLite expand the client the first time this panel opens.
		 */
		@Override
		public Dimension getPreferredSize() {
			Icon icon = getIcon();
			int iconWidth = icon == null ? 0 : icon.getIconWidth();
			int iconHeight = icon == null ? 0 : icon.getIconHeight();
			if (iconWidth <= 0 || iconHeight <= 0)
				return new Dimension(PANEL_WIDTH, 62);
			int width = Math.min(PANEL_WIDTH, iconWidth);
			int height = Math.max(1, (int) Math.round((double) width * iconHeight / iconWidth));
			return new Dimension(width, height);
		}

		private void refreshAspectRatio() {
			Icon icon = getIcon();
			int width = getWidth();
			if (width <= 0 || icon == null || icon.getIconWidth() <= 0 || icon.getIconHeight() <= 0)
				return;
			int height = Math.max(1, (int) Math.round((double) width * icon.getIconHeight() / icon.getIconWidth()));
			if (height == aspectHeight)
				return;
			aspectHeight = height;
			setMinimumSize(new Dimension(0, height));
			// Do not set preferred width to icon.getIconWidth(): it is the native
			// image size, not the available RuneLite sidebar width.
			setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
			if (getParent() != null)
				getParent().revalidate();
		}

		@Override
		public Dimension getMaximumSize() {
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			Icon icon = getIcon();
			if (icon == null || icon.getIconWidth() <= 0 || icon.getIconHeight() <= 0)
				return;
			Graphics2D g = (Graphics2D) graphics.create();
			g.setColor(getBackground());
			g.fillRect(0, 0, getWidth(), getHeight());
			g.scale((double) getWidth() / icon.getIconWidth(), (double) getHeight() / icon.getIconHeight());
			icon.paintIcon(this, g, 0, 0);
			g.dispose();
		}

		@Override
		public boolean imageUpdate(Image image, int flags, int x, int y, int width, int height) {
			if ((flags & (ABORT | ERROR)) != 0)
				return false;

			if ((flags & (FRAMEBITS | ALLBITS | SOMEBITS)) != 0)
				repaint();

			// JLabel/JComponent can stop observing when the sidebar is not yet visible.
			// Keep the GIF observer alive so the animation continues when the panel opens.
			return true;
		}
	}

	private static final class EvidenceImagePanel extends JPanel {
		private final BufferedImage image;

		private EvidenceImagePanel(BufferedImage image) {
			this.image = image;
			setBackground(Color.BLACK);
			setPreferredSize(new Dimension(Math.min(900, image.getWidth()), Math.min(700, image.getHeight())));
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			super.paintComponent(graphics);
			Insets insets = getInsets();
			int availableWidth = Math.max(1, getWidth() - insets.left - insets.right);
			int availableHeight = Math.max(1, getHeight() - insets.top - insets.bottom);
			double factor = Math.min((double) availableWidth / image.getWidth(),
					(double) availableHeight / image.getHeight());
			int width = Math.max(1, (int) Math.round(image.getWidth() * factor));
			int height = Math.max(1, (int) Math.round(image.getHeight() * factor));
			int x = insets.left + (availableWidth - width) / 2;
			int y = insets.top + (availableHeight - height) / 2;
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.drawImage(image, x, y, width, height, null);
			g.dispose();
		}
	}

	private static BufferedImage placeholderAvatar() {
		BufferedImage i = new BufferedImage(58, 58, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = i.createGraphics();
		g.setColor(new Color(40, 52, 68));
		g.fillRect(0, 0, 58, 58);
		g.setColor(Color.WHITE);
		g.setFont(new Font("SansSerif", Font.BOLD, 28));
		g.drawString("Z", 20, 39);
		g.dispose();
		return i;
	}

	private static BufferedImage placeholderCompetition(String kind) {
		BufferedImage i = new BufferedImage(PANEL_WIDTH - 32, 76, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = i.createGraphics();
		g.setColor(new Color(40, 52, 68));
		g.fillRect(0, 0, i.getWidth(), i.getHeight());
		g.setColor(Color.WHITE);
		g.setFont(new Font("SansSerif", Font.BOLD, 20));
		g.drawString(kind, 12, 45);
		g.dispose();
		return i;
	}
}

