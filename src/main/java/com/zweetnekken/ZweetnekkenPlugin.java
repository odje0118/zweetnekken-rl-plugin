/*
 * Portions adapted from the Llama Club and PB Tracker Sync plugins.
 * See LICENSES/llama-LICENSE.txt and LICENSES/pb-tracker-sync-LICENSE.txt.
 */
package com.zweetnekken;

import com.google.inject.Provides;
import com.zweetnekken.evidence.EventPipeline;
import com.zweetnekken.evidence.EvidenceStore;
import com.zweetnekken.events.CollectionLogEventListener;
import com.zweetnekken.events.CombatTierEventListener;
import com.zweetnekken.events.LootEventListener;
import com.zweetnekken.events.WeaponCreationEventListener;
import com.zweetnekken.events.PetEventListener;
import com.zweetnekken.pb.PersonalBestService;
import com.zweetnekken.progress.PlayerProgressService;
import com.zweetnekken.service.ZweetnekkenDataService;
import com.zweetnekken.service.BingoService;
import com.zweetnekken.service.EventAlertService;
import com.zweetnekken.service.ImageCache;
import com.zweetnekken.service.PartyTracker;
import com.zweetnekken.ui.ZweetnekkenPanel;
import com.zweetnekken.ui.BingoEventOverlay;
import com.zweetnekken.ui.EventAlertOverlay;
import java.io.IOException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Filepath;

@Slf4j
@PluginDescriptor(
	name = "Zweetnekken",
	description = "Zweetnekken clan dashboard, evidence capture, loot submissions, and PB sync",
	tags = {"clan", "loot", "pvm", "personal best", "collection log", "pet"},
	internalName = "zweetnekken",
	legacyDataDirectory = "zweetnekken"
)
public class ZweetnekkenPlugin extends Plugin
{
	static final long PROFILE_AND_COMPETITIONS_REFRESH_MINUTES = 15;
	static final long PLAYER_PROGRESS_REFRESH_MINUTES = 30;
	static final long PLAYER_ELIGIBILITY_REFRESH_MINUTES = 1;

	@Inject private Client client;
	@Inject private ClientToolbar toolbar;
	@Inject private EventBus eventBus;
	@Inject private ScheduledExecutorService executor;
	@Inject private ClientThread clientThread;
	@Inject private OverlayManager overlayManager;
	@Inject private ZweetnekkenConfig config;
	@Inject private ConfigManager configManager;
	@Inject private ZweetnekkenPanel panel;
	@Inject private ZweetnekkenDataService data;
	@Inject private BingoService bingo;
	@Inject private EvidenceStore evidence;
	@Inject private EventPipeline pipeline;
	@Inject private ImageCache imageCache;
	@Inject private PersonalBestService pbs;
	@Inject private PlayerProgressService progress;
	@Inject private EventAlertService eventAlerts;
	@Inject private EventAlertOverlay eventAlertOverlay;
	@Inject private BingoEventOverlay bingoEventOverlay;
	@Inject private PartyTracker parties;
	@Inject private LootEventListener loot;
	@Inject private WeaponCreationEventListener weaponCreation;
	@Inject private CollectionLogEventListener collectionLog;
	@Inject private PetEventListener pets;
	@Inject private CombatTierEventListener combatTiers;
	private NavigationButton navigation;
	private ScheduledFuture<?> refreshTask;
	private ScheduledFuture<?> progressTask;
	private ScheduledFuture<?> eligibilityRefreshTask;
	private List<Object> registered;
	private volatile boolean featuresActive;
	private String firstConnectionSyncedAccount;
	private String activePlayerName;
	private final Runnable connectionListener = () -> clientThread.invokeLater(this::updateFeatureActivation);

	@Provides ZweetnekkenConfig provideConfig(ConfigManager manager) { return manager.getConfig(ZweetnekkenConfig.class); }

	@Provides Filepath providePluginDirectory() throws IOException { return getPluginDirectory(); }

	@Override protected void startUp()
	{
		panel.refreshBanner();
		navigation = NavigationButton.builder().tooltip("Zweetnekken").icon(loadIcon()).priority(5).panel(panel).build(); toolbar.addNavigation(navigation);
		overlayManager.add(eventAlertOverlay);
		overlayManager.add(bingoEventOverlay);
		registered = Arrays.asList(parties, loot, weaponCreation, collectionLog, pets, combatTiers, pbs);
		data.addListener(connectionListener);
		data.validate();
		executor.execute(() -> evidence.pruneUploaded(config.retentionDays()));
		if (client.getGameState() == GameState.LOGGED_IN) onLoggedIn();
		refreshTask = executor.scheduleAtFixedRate(this::runScheduledRefresh,
			PROFILE_AND_COMPETITIONS_REFRESH_MINUTES,
			PROFILE_AND_COMPETITIONS_REFRESH_MINUTES,
			TimeUnit.MINUTES);
		progressTask = executor.scheduleAtFixedRate(this::runScheduledProgressSync,
			PLAYER_PROGRESS_REFRESH_MINUTES,
			PLAYER_PROGRESS_REFRESH_MINUTES,
			TimeUnit.MINUTES);
		eligibilityRefreshTask = executor.scheduleAtFixedRate(this::runScheduledEligibilityRefresh,
			PLAYER_ELIGIBILITY_REFRESH_MINUTES,
			PLAYER_ELIGIBILITY_REFRESH_MINUTES,
			TimeUnit.MINUTES);
	}

	@Override protected void shutDown()
	{
		if (refreshTask != null) { refreshTask.cancel(false); refreshTask = null; }
		if (progressTask != null) { progressTask.cancel(false); progressTask = null; }
		if (eligibilityRefreshTask != null) { eligibilityRefreshTask.cancel(false); eligibilityRefreshTask = null; }
		deactivateFeatures();
		registered = null;
		data.removeListener(connectionListener);
		if (navigation != null) { toolbar.removeNavigation(navigation); navigation = null; }
		overlayManager.remove(eventAlertOverlay);
		overlayManager.remove(bingoEventOverlay);
		eventAlerts.resetStateForWorldHopOrLogin();
		bingo.clear();
		activePlayerName = null;
		firstConnectionSyncedAccount = null;
		data.loggedOut();
	}

	@Subscribe public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			eventAlerts.resetStateForLoginScreen();
		}
		else if (event.getGameState() == GameState.HOPPING
			|| event.getGameState() == GameState.CONNECTION_LOST)
		{
			eventAlerts.resetStateForWorldHopOrLogin();
		}
		if (event.getGameState() == GameState.LOGGED_IN) tryOnLoggedIn();
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			deactivateFeatures();
			activePlayerName = null;
			firstConnectionSyncedAccount = null;
			bingo.clear();
			data.loggedOut();
		}
	}

	@Subscribe public void onGameTick(GameTick event)
	{
		tryOnLoggedIn();
		if (config.eventAlerts()) eventAlerts.onGameTick();
	}

	@Subscribe public void onConfigChanged(ConfigChanged event)
	{
		if (!ZweetnekkenConfig.GROUP.equals(event.getGroup())) return;
		switch (event.getKey())
		{
			case "authenticationCode": firstConnectionSyncedAccount = null; data.validate(); updateFeatureActivation(); break;
			case "eventAlerts": eventAlerts.resetStateForConfigChange(); break;
			case "animatedBanner": panel.refreshBanner(); break;
			case "refreshProfile": if (Boolean.parseBoolean(event.getNewValue())) { refreshCurrentPlayer(); resetAction(event.getKey()); } break;
			case "retryOutbox": if (Boolean.parseBoolean(event.getNewValue())) { if (featuresActive) pipeline.retryAll(); resetAction(event.getKey()); } break;
			case "clearImageCache": if (Boolean.parseBoolean(event.getNewValue())) { panel.invalidateBannerLoads(); resetAction(event.getKey()); imageCache.clear(() -> clientThread.invokeLater(() -> { refreshCurrentPlayer(); panel.refreshBanner(); })); } break;
			default: break;
		}
	}

	/** Keeps legacy action config items usable without leaving a checked state behind. */
	private void resetAction(String key) { configManager.setConfiguration(ZweetnekkenConfig.GROUP, key, false); }

	private void onLoggedIn() { tryOnLoggedIn(); }
	private void tryOnLoggedIn()
	{
		if (client.getGameState() != GameState.LOGGED_IN) return;
		String name = currentName();
		if (name == null || name.isBlank()) return;
		if (name.equals(activePlayerName)) { updateFeatureActivation(); return; }
		activePlayerName = name;
		firstConnectionSyncedAccount = null;
		data.refresh(name);
		updateFeatureActivation();
	}

	private void updateFeatureActivation()
	{
		// Region loading can temporarily remove the local player. Preserve feature
		// subscriptions and raid state through that transition; login/hop still reset normally.
		if (client.getGameState() == GameState.LOADING) return;
		String name = currentName();
		if (client.getGameState() == GameState.LOGGED_IN && (name == null || name.isBlank())) return;
		boolean shouldBeActive = client.getGameState() == GameState.LOGGED_IN
			&& name != null && !name.isBlank()
			&& data.connection() == ZweetnekkenDataService.Connection.CONNECTED
			&& data.isCurrentPlayerPluginEligible();
		if (!shouldBeActive)
		{
			deactivateFeatures();
			return;
		}
		if (!featuresActive)
		{
			featuresActive = true;
			weaponCreation.reset();
			for (Object listener : registered) eventBus.register(listener);
			bingo.refresh();
			pbs.onLogin();
			pipeline.retryAll();
			pipeline.refreshStatuses(name);
		}
		syncFirstConnectionData();
	}

	private void deactivateFeatures()
	{
		if (!featuresActive) return;
		featuresActive = false;
		weaponCreation.reset();
		if (registered != null) for (Object listener : registered) eventBus.unregister(listener);
		firstConnectionSyncedAccount = null;
		eventAlerts.resetStateForWorldHopOrLogin();
		bingo.clear();
	}

	private void syncFirstConnectionData()
	{
		if (!featuresActive || !data.isCurrentPlayerPluginEligible()
			|| data.connection() != ZweetnekkenDataService.Connection.CONNECTED
			|| client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null) return;
		String account = Long.toString(client.getAccountHash());
		if ("0".equals(account)) return;
		if (account.equals(firstConnectionSyncedAccount)) return;
		firstConnectionSyncedAccount = account;
		clientThread.invokeLater(progress::syncAll);
		executor.execute(() -> pbs.syncAll(true));
	}
	private void refreshCurrentPlayer()
	{
		String name = currentName();
		if (name == null || name.isBlank()) return;
		data.refresh(name);
		if (featuresActive)
		{
			bingo.refresh();
			pipeline.refreshStatuses(name);
		}
	}
	private void runScheduledRefresh()
	{
		try { refreshCurrentPlayer(); }
		catch (RuntimeException e) { log.error("Unable to refresh Zweetnekken profile and competitions", e); }
	}
	private void runScheduledEligibilityRefresh()
	{
		if (client.getGameState() != GameState.LOGGED_IN) return;
		String name = currentName();
		if (name == null || name.isBlank()) return;
		try { data.refreshProfile(name); }
		catch (RuntimeException e) { log.error("Unable to refresh Zweetnekken plugin eligibility", e); }
	}

	private void runScheduledProgressSync()
	{
		if (!featuresActive) return;
		try { clientThread.invokeLater(progress::syncAll); }
		catch (RuntimeException e) { log.error("Unable to schedule Zweetnekken player progress sync", e); }
	}
	private String currentName() { return client.getLocalPlayer() == null ? null : client.getLocalPlayer().getName(); }

	private BufferedImage loadIcon()
	{
		BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(22, 31, 43));
		g.fillOval(1, 1, 30, 30);
		g.setColor(new Color(207, 164, 76));
		g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 22));
		g.drawString("Z", 7, 24);
		g.dispose();
		return image;
	}
}
