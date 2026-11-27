package com.zweetnekken.service;

import com.zweetnekken.ZweetnekkenConfig;
import com.zweetnekken.api.ZweetnekkenApiClient;
import com.zweetnekken.model.ZweetnekkenModels;
import java.awt.image.BufferedImage;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
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
	private volatile ZweetnekkenModels.PvmDiaryStatus pvmDiaryStatus;
	private volatile ZweetnekkenModels.PvmDiaryConfig pvmDiaryConfig;
	private final AtomicLong diaryRequest = new AtomicLong();
	private volatile String pvmDiaryMessage = "Meld aan om je PvM Diary te laden";
	private volatile ZweetnekkenModels.CompetitionPanels competitionPanels;
	private volatile BufferedImage profileImage;
	private volatile BufferedImage botwImage;
	private volatile BufferedImage sotwImage;
	private volatile BufferedImage unauthenticatedBotwImage;
	private volatile BufferedImage unauthenticatedSotwImage;
	private volatile long botwImageCompetitionId = Long.MIN_VALUE;
	private volatile long sotwImageCompetitionId = Long.MIN_VALUE;
	private volatile Connection connection = Connection.NOT_CONFIGURED;
	private volatile String message = "Meld je aan om je Zweetnekken-profiel te laden";
	private volatile String requestedPlayerName;
	private volatile boolean currentPlayerPluginEligible;
	private volatile boolean currentPlayerCanSubmitLoot;

	public void addListener(Runnable listener) { listeners.add(listener); }
	public void removeListener(Runnable listener) { listeners.remove(listener); }
	public ZweetnekkenModels.Profile profile() { return profile; }
	public ZweetnekkenModels.PvmDiaryConfig pvmDiaryConfig()
	{
		ZweetnekkenModels.PvmDiaryConfig catalogue = pvmDiaryConfig;
		return currentPlayerPluginEligible && catalogue != null && PvmDiaryCatalogue.allows(catalogue, requestedPlayerName)
			? catalogue : null;
	}
	public ZweetnekkenModels.PvmDiaryConfig pvmDiaryConfig(String playerName)
	{
		ZweetnekkenModels.PvmDiaryConfig catalogue = pvmDiaryConfig();
		return catalogue != null && PvmDiaryCatalogue.sameRsn(requestedPlayerName, playerName) ? catalogue : null;
	}
	public ZweetnekkenModels.PvmDiaryStatus pvmDiaryView() { return pvmDiaryView(pvmDiaryStatus()); }
	public ZweetnekkenModels.PvmDiaryStatus pvmDiaryView(ZweetnekkenModels.PvmDiaryStatus progress)
	{
		return PvmDiaryCatalogue.view(pvmDiaryConfig(), progress);
	}
	public boolean isPvmDiaryActivityActive(String playerName, String id)
	{
		ZweetnekkenModels.PvmDiaryConfig catalogue = pvmDiaryConfig(playerName);
		return catalogue != null && catalogue.activities.stream().anyMatch(activity -> activity.id.equals(id));
	}
	public ZweetnekkenModels.PvmDiaryStatus pvmDiaryStatus()
	{
		ZweetnekkenModels.PvmDiaryConfig catalogue = pvmDiaryConfig();
		ZweetnekkenModels.PvmDiaryStatus status = pvmDiaryStatus;
		return catalogue != null && status != null && catalogue.catalogueVersion.equals(status.catalogueVersion)
			? status : null;
	}
	public ZweetnekkenModels.PvmDiaryStatus pvmDiaryStatus(String playerName)
	{
		return PvmDiaryCatalogue.sameRsn(requestedPlayerName, playerName) ? pvmDiaryStatus() : null;
	}
	public String pvmDiaryMessage() { return pvmDiaryMessage; }
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
		diaryRequest.incrementAndGet();
		pvmDiaryConfig = null;
		requestedPlayerName = null;
		currentPlayerPluginEligible = false;
				currentPlayerCanSubmitLoot = false;
		profile = null;
		profileImage = null;
		pvmDiaryStatus = null;
		pvmDiaryMessage = "Meld aan om je PvM Diary te laden";
		message = "Meld je aan om je Zweetnekken-profiel te laden";
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
			diaryRequest.incrementAndGet();
			pvmDiaryConfig = null;
			profile = null;
			profileImage = null;
			pvmDiaryStatus = null;
			pvmDiaryMessage = "PvM Diary wordt geladen…";
			currentPlayerPluginEligible = false;
			currentPlayerCanSubmitLoot = false;
		}
		requestedPlayerName = requestedName;
		message = "Profiel van " + requestedName + " laden…";
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
					? "Niet gevonden in het Zweetnekken-roster"
					: currentPlayerCanSubmitLoot ? "Profiel geladen" : "Trialist — loot inzenden is niet beschikbaar";
				if (currentPlayerPluginEligible)
				{
					refreshPvmDiary(requestedName);
					String avatarPath = profile.member.avatarUrl;
					String url = resolveProfileImageUrl(avatarPath);
					if (url == null) url = resolveProfileImageUrl(profileImagePath(requestedName));
					if (url != null)
					{
						images.loadProfile(requestedName, url, image ->
						{
							if (samePlayer(requestedPlayerName, requestedName) && image != null) profileImage = image;
							notifyListeners();
						});
					}
				}
				else
				{
					profile = null;
					clearPvmDiary();
				}
			}
			else
			{
				profile = null;
				clearPvmDiary();
				currentPlayerPluginEligible = false;
				currentPlayerCanSubmitLoot = false;
				message = result.statusCode == 404 ? "Niet gevonden in het Zweetnekken-roster" : result.error;
			}
			notifyListeners();
		});
	}

	private void clearPvmDiary()
	{
		diaryRequest.incrementAndGet();
		pvmDiaryConfig = null;
		pvmDiaryStatus = null;
		pvmDiaryMessage = "PvM Diary is niet beschikbaar voor dit account";
	}

	public void refreshPvmDiary(String playerName)
	{
		if (!currentPlayerPluginEligible || !PvmDiaryCatalogue.sameRsn(requestedPlayerName, playerName)) return;
		String code = config.authenticationCode();
		if (code == null || code.isBlank()) return;
		long request = diaryRequest.incrementAndGet();
		api.getPvmDiaryConfig(playerName, result ->
		{
			if (request != diaryRequest.get() || !PvmDiaryCatalogue.sameRsn(requestedPlayerName, playerName)) return;
			ZweetnekkenModels.PvmDiaryConfig catalogue = result.value;
			if (!result.isSuccessful() || !PvmDiaryCatalogue.isActive(catalogue)
				|| !PvmDiaryCatalogue.allows(catalogue, playerName))
			{
				pvmDiaryConfig = null;
				pvmDiaryStatus = null;
				pvmDiaryMessage = catalogue != null && !catalogue.enabled
					? "PvM Diary staat uit" : "PvM Diary-configuratie is niet beschikbaar";
				notifyListeners();
				return;
			}
			pvmDiaryMessage = "PvM Diary-voortgang wordt geladen…";
			pvmDiaryConfig = catalogue;
			notifyListeners();
			api.getPvmDiaryStatus(playerName, playerName, status ->
			{
				if (request != diaryRequest.get() || !PvmDiaryCatalogue.sameRsn(requestedPlayerName, playerName)) return;
				pvmDiaryStatus = status.isSuccessful() ? status.value : null;
				pvmDiaryMessage = pvmDiaryStatus() != null ? "" : status.statusCode == 404
					? "Er is nog geen PvM Diary-status gepubliceerd voor dit lid"
					: "PvM Diary-voortgang is niet beschikbaar voor deze catalogus";
				notifyListeners();
			});
		});
	}

	/** Loads a roster profile for the member-search page without changing active-account permissions. */
	public void lookupMemberProfile(String playerName,
		ZweetnekkenApiClient.ResultCallback<ZweetnekkenModels.Profile> callback)
	{
		api.getProfile(playerName, callback);
	}

	/** Loads a member's public PvM Diary without replacing the logged-in player's own diary state. */
	public void lookupMemberPvmDiary(String playerName,
		ZweetnekkenApiClient.ResultCallback<ZweetnekkenModels.PvmDiaryStatus> callback)
	{
		if (pvmDiaryConfig() == null)
		{
			callback.complete(ZweetnekkenApiClient.ApiResult.error(503, "PvM Diary is niet beschikbaar"));
			return;
		}
		api.getPvmDiaryStatus(playerName, requestedPlayerName, callback);
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

	static String resolveProfileImageUrl(String relativePath)
	{
		if (relativePath == null || relativePath.isBlank()) return null;
		String path = relativePath.trim();
		// Profile avatar paths must stay on the configured Zweetnekken API origin.
		// Never accept absolute or protocol-relative URLs from the API response.
		if (!path.startsWith("/") || path.startsWith("//") || path.contains("://")) return null;
		return ZWEETNEKKEN_API_ORIGIN + path;
	}

	static String profileImagePath(String playerName)
	{
		return "/api/profile-portrait?name="
			+ URLEncoder.encode(playerName == null ? "" : playerName, StandardCharsets.UTF_8).replace("+", "%20");
	}

	public void validate()
	{
		clearPvmDiary();
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
