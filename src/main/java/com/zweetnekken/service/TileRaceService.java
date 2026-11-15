package com.zweetnekken.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zweetnekken.ZweetnekkenConfig;
import com.zweetnekken.api.ZweetnekkenApiClient;
import com.zweetnekken.model.ZweetnekkenModels;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Polls the bot-calculated standings for the active Tile Race. */
@Singleton
public class TileRaceService
{
	private static final Logger log = LoggerFactory.getLogger(TileRaceService.class);
	private static final int MAX_TEAMS = 4;
	private final ZweetnekkenApiClient api;
	private final ZweetnekkenConfig config;
	private final AtomicBoolean requestInFlight = new AtomicBoolean();
	private final AtomicLong generation = new AtomicLong();
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	private volatile List<ZweetnekkenModels.EventPanelEvent> currentEvents = Collections.emptyList();

	@Inject
	public TileRaceService(ZweetnekkenApiClient api, ZweetnekkenConfig config)
	{
		this.api = api;
		this.config = config;
	}

	public List<ZweetnekkenModels.EventPanelEvent> currentEvents()
	{
		return currentEvents;
	}

	public void addListener(Runnable listener) { listeners.add(listener); }
	public void removeListener(Runnable listener) { listeners.remove(listener); }
	public void clear()
	{
		generation.incrementAndGet();
		currentEvents = Collections.emptyList();
		for (Runnable listener : listeners) listener.run();
	}

	public void refresh()
	{
		if (config.authenticationCode() == null || config.authenticationCode().trim().isEmpty()) return;
		if (!requestInFlight.compareAndSet(false, true)) return;
		long requestGeneration = generation.get();
		try
		{
			api.getTileRaceStandings(result ->
			{
				try
				{
					if (requestGeneration != generation.get()) return;
					if (!result.isSuccessful())
					{
						// Keep the last valid snapshot during network, server, or auth errors.
						log.warn("Tile Race standings request failed (HTTP {}); retaining the last known standings",
							result.statusCode);
						return;
					}
					List<ZweetnekkenModels.EventPanelEvent> parsed = parse(result.value);
					if (parsed == null)
					{
						log.warn("Tile Race standings response did not contain a recognized event state; retaining the last known standings");
						return;
					}
					currentEvents = parsed;
					int displayedTeams = parsed.stream().mapToInt(event ->
						event == null || event.entries == null ? 0 : event.entries.size()).sum();
					log.debug("Refreshed Tile Race standings successfully (active={}, displayedTeams={})",
						!parsed.isEmpty(), displayedTeams);
					for (Runnable listener : listeners) listener.run();
				}
				catch (RuntimeException e)
				{
					log.warn("Unable to process Tile Race standings; retaining the last known standings", e);
				}
				finally
				{
					requestInFlight.set(false);
				}
			});
		}
		catch (RuntimeException e)
		{
			requestInFlight.set(false);
			log.warn("Unable to schedule Tile Race standings request; retaining the last known standings", e);
		}
	}

	/** Returns null for an unrecognized payload, and an empty list for a recognized inactive event. */
	static List<ZweetnekkenModels.EventPanelEvent> parse(JsonObject response)
	{
		if (response == null) return null;
		JsonObject root = object(response, "data");
		if (root == null) root = response;
		JsonObject event = object(root, "event", "tile_race", "tileRace", "active_event", "activeEvent");
		if (event == null) event = root;

		Boolean active = booleanValue(root, "active", "is_active", "isActive");
		if (active == null && event != root) active = booleanValue(event, "active", "is_active", "isActive");
		if (Boolean.FALSE.equals(active)) return Collections.emptyList();

		JsonArray teams = array(root, "teams", "standings", "team_standings", "teamStandings");
		if (teams == null && event != root) teams = array(event, "teams", "standings", "team_standings", "teamStandings");
		JsonElement eventElement = first(root, "event", "tile_race", "tileRace", "active_event", "activeEvent");
		boolean explicitNoEvent = (eventElement != null && eventElement.isJsonNull())
			|| Boolean.FALSE.equals(booleanValue(root, "has_active_event", "hasActiveEvent"));
		if (explicitNoEvent) return Collections.emptyList();

		String title = stringValue(event, "event_name", "eventName", "title", "name");
		if (!present(title)) title = stringValue(root, "event_name", "eventName", "title");
		boolean recognizedActive = Boolean.TRUE.equals(active)
			|| event != root || teams != null || present(title);
		if (!recognizedActive) return null;
		if (teams == null || teams.size() == 0) return Collections.singletonList(eventView(value(title, "Tile Race"), Collections.emptyList()));

		List<ZweetnekkenModels.EventPanelEntry> entries = new ArrayList<>();
		int teamRank = 0;
		for (JsonElement teamElement : teams)
		{
			teamRank++;
			if (teamRank > MAX_TEAMS) break;
			if (!teamElement.isJsonObject()) continue;
			JsonObject team = teamElement.getAsJsonObject();
			String name = stringValue(team, "team_name", "teamName", "name");
			if (!present(name)) continue;
			String completed = countValue(team, "completed_tiles", "completedTiles", "tiles_completed", "tilesCompleted", "completed_count", "completedCount");
			String total = countValue(team, "total_tiles", "totalTiles", "tile_count", "tileCount", "total_count", "totalCount");
			entries.add(new ZweetnekkenModels.EventPanelEntry(teamRank, name,
				value(completed, "—") + "/" + value(total, "—"), progress(completed, total)));
		}
		return Collections.singletonList(eventView(value(title, "Tile Race"), entries));
	}

	private static ZweetnekkenModels.EventPanelEvent eventView(String name, List<ZweetnekkenModels.EventPanelEntry> entries)
	{
		ZweetnekkenModels.EventPanelEvent view = new ZweetnekkenModels.EventPanelEvent();
		view.name = name;
		view.entries = entries;
		return view;
	}

	private static JsonObject object(JsonObject source, String... keys)
	{
		JsonElement element = first(source, keys);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
	}

	private static JsonArray array(JsonObject source, String... keys)
	{
		JsonElement element = first(source, keys);
		return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
	}

	private static JsonElement first(JsonObject source, String... keys)
	{
		if (source == null) return null;
		for (String key : keys)
			if (source.has(key)) return source.get(key);
		return null;
	}

	private static String stringValue(JsonObject source, String... keys)
	{
		JsonElement element = first(source, keys);
		return element == null || element.isJsonNull() || !element.isJsonPrimitive() ? null : element.getAsString();
	}

	private static String countValue(JsonObject source, String... keys)
	{
		JsonElement element = first(source, keys);
		if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) return null;
		try
		{
			int count = element.getAsInt();
			return count < 0 ? null : Integer.toString(count);
		}
		catch (NumberFormatException | UnsupportedOperationException e)
		{
			return null;
		}
	}

	private static double progress(String completed, String total)
	{
		try
		{
			int done = Integer.parseInt(completed);
			int all = Integer.parseInt(total);
			return all <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, (double) done / all));
		}
		catch (NumberFormatException e)
		{
			return 0.0;
		}
	}

	private static Boolean booleanValue(JsonObject source, String... keys)
	{
		JsonElement element = first(source, keys);
		if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) return null;
		try { return element.getAsBoolean(); }
		catch (IllegalStateException e) { return null; }
	}

	private static boolean present(String value) { return value != null && !value.trim().isEmpty(); }
	private static String value(String value, String fallback) { return present(value) ? value.trim() : fallback; }
}
