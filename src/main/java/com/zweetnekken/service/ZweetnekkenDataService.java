package com.zweetnekken.service;

import com.zweetnekken.ZweetnekkenConfig;
import com.zweetnekken.api.ZweetnekkenApiClient;
import com.zweetnekken.model.ZweetnekkenModels;
import java.awt.image.BufferedImage;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ZweetnekkenDataService
{
	static final String ZWEETNEKKEN_API_ORIGIN = ZweetnekkenApiClient.ZWEETNEKKEN_API_ORIGIN;
	// Replace these with the public images shown when authentication is missing or invalid.
	static final String UNAUTHENTICATED_BOTW_IMAGE_URL = "";
	static final String UNAUTHENTICATED_SOTW_IMAGE_URL = "";

	public enum Connection { NOT_CONFIGURED, CHECKING, CONNECTED, INVALID, UNAVAILABLE }
	@Inject private ZweetnekkenApiClient api;
	@Inject private ZweetnekkenConfig config;
	@Inject private ImageCache images;
	@Inject private RulesService rules;
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	private volatile ZweetnekkenModels.Profile profile;
	private volatile ZweetnekkenModels.CompetitionPanels competitionPanels;
	private volatile BufferedImage profileImage;
	private volatile BufferedImage botwImage;
	private volatile BufferedImage sotwImage;
	private volatile BufferedImage unauthenticatedBotwImage;
	private volatile BufferedImage unauthenticatedSotwImage;
	private volatile long botwImageCompetitionId = Long.MIN_VALUE;
	private volatile long sotwImageCompetitionId = Long.MIN_VALUE;
	private volatile Connection connection = Connection.NOT_CONFIGURED;
	private volatile String message = "Log in to load your Zweetnekken profile";
	private volatile String requestedPlayerName;
	private volatile boolean currentPlayerPluginEligible;
	private volatile boolean currentPlayerCanSubmitLoot;

	public void addListener(Runnable listener) { listeners.add(listener); }
	public void removeListener(Runnable listener) { listeners.remove(listener); }
	public ZweetnekkenModels.Profile profile() { return profile; }
	public ZweetnekkenModels.CompetitionPanels competitionPanels() { return competitionPanels; }
	public BufferedImage profileImage() { return profileImage; }
	public BufferedImage botwImage() { return botwImage; }
	public BufferedImage sotwImage() { return sotwImage; }
	public BufferedImage unauthenticatedBotwImage() { return unauthenticatedBotwImage; }
	public BufferedImage unauthenticatedSotwImage() { return unauthenticatedSotwImage; }
	public Connection connection() { return connection; }
	public String message() { return message; }
	/** True for full clan members and approved Trialists who may use the plugin. */
	public boolean isCurrentPlayerPluginEligible() { return currentPlayerPluginEligible; }
	/** Legacy name retained for any existing internal callers; now means plugin eligibility. */
	public boolean isCurrentPlayerClanMember() { return currentPlayerPluginEligible; }
	/** Only full Zweetnek members and higher ranks may send ordinary loot submissions. */
	public boolean canSubmitLoot() { return currentPlayerCanSubmitLoot; }

	public void loggedOut()
	{
		requestedPlayerName = null;
		currentPlayerPluginEligible = false;
				currentPlayerCanSubmitLoot = false;
		profile = null;
		profileImage = null;
		message = "Log in to load your Zweetnekken profile";
		notifyListeners();
	}

	public void refresh(String playerName)
	{
		if (playerName == null || playerName.isBlank()) { loggedOut(); return; }
		refreshProfile(playerName);
		api.getCompetitionPanels(panelResult ->
		{
			ZweetnekkenModels.CompetitionPanels panels = panelResult.isSuccessful() ? panelResult.value : null;
			api.getCompetitions(compResult ->
			{
				ZweetnekkenModels.CompetitionPanels resolved = panels;
				if (compResult.isSuccessful() && compResult.value != null)
				{
					if (resolved == null) resolved = new ZweetnekkenModels.CompetitionPanels();
					resolved.botw = resolvePanel(resolved.botw, compResult.value.botw, "BOTW");
					resolved.sotw = resolvePanel(resolved.sotw, compResult.value.sotw, "SOTW");
				}
				competitionPanels = resolved;
				loadCompetitionImages(resolved);
				notifyListeners();
			});
		});
		loadUnauthenticatedCompetitionImages();
		rules.refresh(this::notifyListeners);
	}

	/**
	 * Refresh only the active player's roster eligibility and loot permission.
	 * Kept separate from the larger dashboard refresh so role changes (for example,
	 * promotion from Trialist to Zweetnek) become effective without reloading
	 * competitions and images every time.
	 */
	public void refreshProfile(String playerName)
	{
		if (playerName == null || playerName.isBlank()) { loggedOut(); return; }
		String requestedName = playerName.trim();
		if (!samePlayer(requestedPlayerName, requestedName))
		{
			profile = null;
			profileImage = null;
			currentPlayerPluginEligible = false;
			currentPlayerCanSubmitLoot = false;
		}
		requestedPlayerName = requestedName;
		message = "Loading " + requestedName + "…";
		notifyListeners();
		api.getProfile(requestedName, result ->
		{
			// An account switch can complete before the previous profile request. Never
			// let that stale response activate the plugin for the newly logged-in RSN.
			if (!samePlayer(requestedPlayerName, requestedName)) return;
			if (result.isSuccessful() && result.value != null)
			{
				profile = result.value;
				// A roster profile itself grants plugin eligibility; loot permission is
				// deliberately checked separately and remains false for Trialists.
				currentPlayerPluginEligible = profile.member != null;
				currentPlayerCanSubmitLoot = currentPlayerPluginEligible && profile.member.canSubmitLoot;
				message = !currentPlayerPluginEligible
					? "Not found in Zweetnekken roster"
					: currentPlayerCanSubmitLoot ? "Profile loaded" : "Trialist — loot submitten is niet beschikbaar";
				if (currentPlayerPluginEligible)
				{
					String url = profile.member.avatarUrl;
					if (url == null || url.isBlank()) url = profileImageUrl(requestedName);
					images.loadProfile(requestedName, url, image ->
					{
						if (samePlayer(requestedPlayerName, requestedName) && image != null) profileImage = image;
						notifyListeners();
					});
				}
				else profile = null;
			}
			else
			{
				profile = null;
				currentPlayerPluginEligible = false;
				currentPlayerCanSubmitLoot = false;
				message = result.statusCode == 404 ? "Not found in Zweetnekken roster" : result.error;
			}
			notifyListeners();
		});
	}

	private void loadUnauthenticatedCompetitionImages()
	{
		if (!UNAUTHENTICATED_BOTW_IMAGE_URL.isBlank())
		{
			images.loadWebsiteImage("unauthenticated-botw", UNAUTHENTICATED_BOTW_IMAGE_URL,
				image -> { unauthenticatedBotwImage = image; notifyListeners(); });
		}
		if (!UNAUTHENTICATED_SOTW_IMAGE_URL.isBlank())
		{
			images.loadWebsiteImage("unauthenticated-sotw", UNAUTHENTICATED_SOTW_IMAGE_URL,
				image -> { unauthenticatedSotwImage = image; notifyListeners(); });
		}
	}

	private static ZweetnekkenModels.CompetitionPanel resolvePanel(ZweetnekkenModels.CompetitionPanel panel,
		ZweetnekkenModels.CompetitionPair pair, String kind)
	{
		ZweetnekkenModels.Competition selected = selectCompetition(pair);
		if (selected == null) return panel;
		if (panel != null && panel.competitionId == selected.id)
		{
			return panel;
		}
		return CompetitionService.convertToPanel(selected, kind);
	}

	static ZweetnekkenModels.Competition selectCompetition(ZweetnekkenModels.CompetitionPair pair)
	{
		if (pair == null) return null;
		if (pair.current != null) return pair.current;
		if (pair.upcoming != null) return pair.upcoming;
		return pair.previous;
	}

	private void loadCompetitionImages(ZweetnekkenModels.CompetitionPanels panels)
	{
		loadCompetitionImage("botw", panels == null ? null : panels.botw);
		loadCompetitionImage("sotw", panels == null ? null : panels.sotw);
	}

	static boolean samePlayer(String first, String second)
	{
		return normalizePlayerName(first).equals(normalizePlayerName(second));
	}

	private static String normalizePlayerName(String name)
	{
		return name == null ? "" : name.trim().toLowerCase(java.util.Locale.ROOT)
			.replace(" ", "").replace("_", "").replace("-", "");
	}

	private void loadCompetitionImage(String kind, ZweetnekkenModels.CompetitionPanel panel)
	{
		long competitionId = panel == null ? 0 : panel.competitionId;
		if ("botw".equals(kind))
		{
			if (botwImageCompetitionId != competitionId) botwImage = null;
			botwImageCompetitionId = competitionId;
		}
		else
		{
			if (sotwImageCompetitionId != competitionId) sotwImage = null;
			sotwImageCompetitionId = competitionId;
		}
		String url = ZWEETNEKKEN_API_ORIGIN + "/" + kind + ".png";
		if (competitionId <= 0) { images.loadWebsiteImage(kind, url, image -> setCompetitionImage(kind, competitionId, image)); return; }
		String key = kind + "-" + competitionId;
		images.loadWebsiteImage(key, url, image -> setCompetitionImage(kind, competitionId, image));
	}

	private void setCompetitionImage(String kind, long competitionId, BufferedImage image)
	{
		if ("botw".equals(kind))
		{
			if (botwImageCompetitionId == competitionId) botwImage = image;
		}
		else if (sotwImageCompetitionId == competitionId) sotwImage = image;
		notifyListeners();
	}

	static String profileImageUrl(String playerName)
	{
		return ZWEETNEKKEN_API_ORIGIN + "/api/profile-portrait?name="
			+ URLEncoder.encode(playerName == null ? "" : playerName, StandardCharsets.UTF_8).replace("+", "%20");
	}

	public void validate()
	{
		String code = config.authenticationCode() == null ? "" : config.authenticationCode().trim();
		if (code.isEmpty()) { connection = Connection.NOT_CONFIGURED; notifyListeners(); return; }
		connection = Connection.CHECKING; notifyListeners();
		api.validateSession(result ->
		{
			connection = result.isSuccessful() ? Connection.CONNECTED
				: result.statusCode == 401 || result.statusCode == 403 ? Connection.INVALID : Connection.UNAVAILABLE;
			notifyListeners();
		});
	}

	private void notifyListeners() { for (Runnable listener : listeners) listener.run(); }
}
