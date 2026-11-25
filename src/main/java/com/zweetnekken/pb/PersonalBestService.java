/*
 * PB profile and Adventure Log parsing adapted from the PB Tracker Sync plugin.
 * See LICENSES/pb-tracker-sync-LICENSE.txt.
 */
package com.zweetnekken.pb;

import com.zweetnekken.api.ZweetnekkenApiClient;
import com.zweetnekken.evidence.EventPipeline;
import com.zweetnekken.model.ZweetnekkenModels;
import com.zweetnekken.service.PartyTracker;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.util.Text;

@Slf4j
@Singleton
public class PersonalBestService
{
	private static final String GROUP = "personalbest";
	private static final Pattern RECORD = Pattern.compile("^Fastest (?<descriptor>.+): (?<value>-|[0-9:]+(?:\\.[0-9]+)?)$");
	private static final Pattern TEAM = Pattern.compile("(\\d+)\\+? player", Pattern.CASE_INSENSITIVE);
	private static final Pattern SPECIAL_ACTIVITY_KILL_COUNT = Pattern.compile(
		"^Your (?<boss>TzTok-Jad|TzKal-Zuk|Sol Heredit|(?:The )?Corrupted Gauntlet|Gauntlet) "
			+ "(?:kill |completion )?count is: [0-9,]+\\.?$", Pattern.CASE_INSENSITIVE);
	private static final Pattern SPECIAL_ACTIVITY_COMPLETION = Pattern.compile(
		"^(?:Congratulations!?\\s*)?(?:You have )?(?:completed|defeated|killed) (?:the )?"
			+ "(?<boss>Fortis Colosseum|Colosseum|Inferno|TzKal-Zuk|TzHaar Fight Cave|Fight Caves|"
			+ "The Corrupted Gauntlet|Corrupted Gauntlet|Gauntlet|Sol Heredit|TzTok-Jad)\\.?$", Pattern.CASE_INSENSITIVE);
	private static final Pattern NEW_PB_DURATION = Pattern.compile(
		"^Duration:?\\s*(?<time>[0-9:]+(?:\\.[0-9]+)?)\\.?\\s*\\(new personal best\\)\\.?$",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern COX_COMPLETION = Pattern.compile(
		"Team size:\\s*(?<team>[1-9][0-9]*|Solo)\\s*(?:players?)?\\s+Duration:\\s*(?<time>[0-9:]+(?:\\.[0-9]+)?)"
			+ "(?:\\s+Personal best:\\s*[0-9:]+(?:\\.[0-9]+)?|\\s*\\(new personal best\\))"
			+ "(?:\\s+Olm duration:.*)?$",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern COX_COMPLETION_COUNT = Pattern.compile(
		"^Your completed Chambers of Xeric(?<challenge> Challenge Mode)? count is: [0-9,]+\\.?$",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern TOTAL_RAID_COMPLETION_DURATION = Pattern.compile(
		"\\btotal\\s+completion time:\\s*(?<time>[0-9:]+(?:\\.[0-9]+)?)(?:\\.|\\s|$)",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern ARCHIVE_COMPLETION = Pattern.compile(
		"^(?:The )?Fractured Archive: Total completion time: (?<time>[0-9:]+(?:\\.[0-9]+)?)"
			+ "(?: \\(new personal best\\)|\\. Personal best: (?<best>[0-9:]+(?:\\.[0-9]+)?))\\.?$",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern TOB_COMPLETION_DURATION = Pattern.compile(
		"(?:^|\\bTheatre of Blood(?::?\\s+Hard Mode)?\\s+)completion time:\\s*(?<time>[0-9:]+(?:\\.[0-9]+)?)(?:\\.|\\s|$)",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern TOB_HARD_MODE_COMPLETION = Pattern.compile(
		"^Wave 'The Final Challenge' \\(Hard Mode\\) complete!?$", Pattern.CASE_INSENSITIVE);
	private static final Pattern OBSERVED_ACTIVITY_DURATION = Pattern.compile(
		"(?:Fight |Challenge |Corrupted challenge |Colosseum )?duration:?\\s*(?<time>[0-9:]+(?:\\.[0-9]+)?)"
			+ "(?:\\.\\s*Personal best:\\s*[0-9:]+(?:\\.[0-9]+)?|\\s*\\(new personal best\\)|[.]?$)",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern DOOM_COMPLETION_TITLE = Pattern.compile("^Level\\s+(?<level>[1-9][0-9]*)\\s+Complete!$",
		Pattern.CASE_INSENSITIVE);
	private static final long RAID_COMPLETION_TIMEOUT_MILLIS = 15_000L;
	private static final long COX_COMPLETION_CONFIRMATION_TIMEOUT_MILLIS = 5_000L;
	private static final Set<String> DUPLICATE_KEYS = new HashSet<>(java.util.Arrays.asList(
		"tztok-jad", "tzkal-zuk", "sol heredit", "hueycoatl", "gauntlet", "corrupted gauntlet", "nightmare",
		"tzhaar fight cave", "inferno", "fortis colosseum", "the gauntlet", "the corrupted gauntlet",
		"the hueycoatl", "phosani's nightmare", "phosanis nightmare"
	));

	static boolean isAmbiguous(String key)
	{
		String lower = key.toLowerCase(Locale.ROOT).trim();
		if (DUPLICATE_KEYS.contains(lower)) return true;
		return lower.startsWith("chambers of xeric")
			|| lower.startsWith("theatre of blood")
			|| lower.startsWith("tombs of amascut")
			|| isFracturedArchiveKey(lower)
			|| lower.startsWith("nightmare");
	}
	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private ConfigManager configManager;
	@Inject private ZweetnekkenApiClient api;
	@Inject private EventPipeline pipeline;
	@Inject private PartyTracker parties;
	private final Map<String, Double> known = new HashMap<>();
	private volatile String status = "Nog niet gesynchroniseerd";
	private String lastFingerprint;
	private boolean journalLoaded;
	private Scoreboard pendingScoreboard;
	private String pendingDiaryActivityId;
	private long pendingDiaryActivityUntil;
	private Double pendingDiaryDurationSeconds;
	private long pendingDiaryDurationUntil;
	private String pendingRaidActivity;
	private Integer pendingRaidTeamSize;
	private Integer pendingRaidInvocation;
	private long pendingRaidAt;
	private Long pendingRaidTotalDurationMillis;
	private Long pendingTobDurationMillis;
	private ZweetnekkenModels.PbRecord pendingCoxCompletion;
	private long pendingCoxCompletionAt;

	public String status() { return status; }
	public void onLogin()
	{
		pendingScoreboard = null;
		journalLoaded = false;
		pendingDiaryActivityId = null;
		pendingDiaryDurationSeconds = null;
		pendingRaidActivity = null;
		pendingRaidTeamSize = null;
		pendingRaidInvocation = null;
		pendingRaidTotalDurationMillis = null;
		pendingTobDurationMillis = null;
		pendingCoxCompletion = null;
		hydrateKnown();
	}

	@Subscribe public void onConfigChanged(ConfigChanged event)
	{
		if (!GROUP.equals(event.getGroup()) || event.getNewValue() == null) return;
		// The personalbest profile is the source of truth for PB values.
		// Normalize the raw config key before storing or uploading it.
		double seconds; try { seconds = Double.parseDouble(event.getNewValue()); } catch (NumberFormatException e) { return; }
		ZweetnekkenModels.PbRecord record = pbRecordFromKey(event.getKey(), seconds);
		String key = recordKey(record);
		Double previous = known.put(key, seconds);
		if (previous != null && seconds >= previous) return;
		sync(java.util.Collections.singletonList(record), false, true, previous, false);
	}

	@Subscribe public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM) return;
		String rawMessage = event.getMessage().replaceAll("(?i)<br\\s*/?>", " ");
		String message = Text.removeTags(rawMessage).replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
		// Keep the Fractured Archive PB parser; PvM Diary event submission is parked.
		captureArchiveCompletion(message);
		captureDiaryCompletion(message);
	}

	private void captureDiaryCompletion(String message)
	{
		if (capturePendingRaid(message)) return;
		captureBossDiaryCompletion(message);
		Matcher cox = COX_COMPLETION.matcher(message);
		if (cox.matches())
		{
			Double seconds = parseTime(cox.group("time"));
			int teamSize = "solo".equalsIgnoreCase(cox.group("team")) ? 1 : parseTeamSize(cox.group("team"));
			if (seconds != null && teamSize > 0)
			{
				capturePendingCoxCompletion();
				pendingCoxCompletion = new ZweetnekkenModels.PbRecord();
				pendingCoxCompletion.activity = "Chambers of Xeric";
				pendingCoxCompletion.teamSize = teamSize;
				pendingCoxCompletion.durationMillis = Math.round(seconds * 1000);
				pendingCoxCompletionAt = System.currentTimeMillis();
			}
			return;
		}
		if (pendingCoxCompletion != null)
		{
			Matcher coxCount = COX_COMPLETION_COUNT.matcher(message);
			if (coxCount.matches())
			{
				if (coxCount.group("challenge") != null) pendingCoxCompletion.variant = "challenge_mode";
				capturePendingCoxCompletion();
				return;
			}
			if (System.currentTimeMillis() - pendingCoxCompletionAt > COX_COMPLETION_CONFIRMATION_TIMEOUT_MILLIS)
				capturePendingCoxCompletion();
		}

		Matcher doom = DOOM_COMPLETION_TITLE.matcher(message);
		if (doom.matches())
		{
			captureDiaryRun("doom", Long.parseLong(doom.group("level")), "Doom");
		}
	}

	private void capturePendingCoxCompletion()
	{
		ZweetnekkenModels.PbRecord record = pendingCoxCompletion;
		pendingCoxCompletion = null;
		if (record == null || record.durationMillis == null || record.teamSize == null) return;
		String prefix = "challenge_mode".equals(record.variant) ? "cox-cm-" : "cox-";
		String id = record.teamSize == 1 ? prefix + "solo" : record.teamSize == 3 ? prefix + "trios"
			: record.teamSize == 5 ? prefix + "5s" : null;
		if (id != null) captureDiaryRun(id, record.durationMillis, "Chambers of Xeric");
	}

	private boolean capturePendingRaid(String message)
	{
		String activity = raidActivityFromCompletionMessage(message);
		if (activity != null)
		{
			pendingRaidActivity = activity;
			pendingRaidTeamSize = parties.confirmedRaidTeamSize(activity);
			pendingRaidInvocation = activity.startsWith("Tombs of Amascut")
				? client.getVarbitValue(VarbitID.TOA_CLIENT_RAID_LEVEL) : null;
			pendingRaidAt = System.currentTimeMillis();
		}

		Long totalDuration = totalCompletionTimeMillis(message);
		if (totalDuration != null)
		{
			pendingRaidTotalDurationMillis = totalDuration;
			pendingRaidAt = System.currentTimeMillis();
		}
		Long tobDuration = tobCompletionTimeMillis(message);
		if (tobDuration != null)
		{
			pendingTobDurationMillis = tobDuration;
			pendingRaidAt = System.currentTimeMillis();
		}
		return tryCapturePendingRaid();
	}

	private static String raidActivityFromCompletionMessage(String message)
	{
		if (message == null) return null;
		if (TOB_HARD_MODE_COMPLETION.matcher(message.trim()).matches()) return "Theatre of Blood Hard Mode";
		String lower = message.toLowerCase(Locale.ROOT);
		boolean completionSignal = lower.contains("count is:") || lower.contains("raid is complete")
			|| lower.contains("raid complete") || lower.contains("completed the raid")
			|| lower.contains("raid completion") || lower.contains("total completion time");
		if (!completionSignal) return null;
		if (lower.contains("theatre of blood"))
			return lower.contains("hard mode") ? "Theatre of Blood Hard Mode" : "Theatre of Blood";
		if (lower.contains("tombs of amascut"))
			return lower.contains("expert mode") ? "Tombs of Amascut Expert Mode" : "Tombs of Amascut";
		return null;
	}

	private Long totalCompletionTimeMillis(String message)
	{
		Matcher matcher = TOTAL_RAID_COMPLETION_DURATION.matcher(message == null ? "" : message);
		if (!matcher.find()) return null;
		Double seconds = parseTime(matcher.group("time"));
		return seconds == null ? null : Math.round(seconds * 1000);
	}

	private Long tobCompletionTimeMillis(String message)
	{
		Matcher matcher = TOB_COMPLETION_DURATION.matcher(message == null ? "" : message);
		if (!matcher.find()) return null;
		Double seconds = parseTime(matcher.group("time"));
		return seconds == null ? null : Math.round(seconds * 1000);
	}

	private boolean tryCapturePendingRaid()
	{
		long age = System.currentTimeMillis() - pendingRaidAt;
		if (pendingRaidActivity == null || pendingRaidTeamSize == null
			|| age < 0 || age > RAID_COMPLETION_TIMEOUT_MILLIS) return false;
		Long duration = pendingRaidActivity.startsWith("Theatre of Blood")
			? pendingTobDurationMillis : pendingRaidTotalDurationMillis;
		if (duration == null) return false;
		String sourceName = pendingRaidActivity;
		String id = null;
		Object result = duration;
		int team = pendingRaidTeamSize;
		if (pendingRaidActivity.startsWith("Theatre of Blood"))
		{
			boolean hardMode = pendingRaidActivity.contains("Hard Mode");
			id = hardMode ? (team == 2 ? "hmt-duo" : team == 3 ? "hmt-trios" : team == 4 ? "hmt-4s" : team == 5 ? "hmt-5s" : null)
				: (team == 1 ? "tob-solo" : team == 2 ? "tob-duo" : team == 3 ? "tob-trio" : team == 4 ? "tob-4s" : team == 5 ? "tob-5s" : null);
			if ("tob-solo".equals(id) || "hmt-duo".equals(id)) result = "completion";
		}
		else if (pendingRaidActivity.startsWith("Tombs of Amascut") && team == 1)
		{
			id = Integer.valueOf(300).equals(pendingRaidInvocation) ? "toa-300-solo"
				: Integer.valueOf(500).equals(pendingRaidInvocation) ? "toa-500-solo" : null;
		}
		pendingRaidActivity = null;
		pendingRaidTotalDurationMillis = null;
		pendingTobDurationMillis = null;
		pendingRaidTeamSize = null;
		pendingRaidInvocation = null;
		if (id != null) captureDiaryRun(id, result, sourceName);
		return id != null;
	}

	private void captureBossDiaryCompletion(String message)
	{
		long now = System.currentTimeMillis();
		Matcher completed = SPECIAL_ACTIVITY_COMPLETION.matcher(message);
		if (completed.matches())
		{
			String boss = completed.group("boss").toLowerCase(Locale.ROOT);
			String id = boss.contains("fight cave") || boss.equals("fight caves") ? "fight-caves-solo"
				: boss.contains("inferno") || boss.contains("tzkal-zuk") ? "inferno-solo"
				: boss.contains("colosseum") || boss.contains("sol heredit") ? "colosseum-solo"
				: boss.contains("corrupted gauntlet") ? "cg-solo" : null;
			if (id != null)
			{
				pendingDiaryActivityId = id;
				pendingDiaryActivityUntil = now + 6_000L;
				if (pendingDiaryDurationSeconds != null && now <= pendingDiaryDurationUntil)
				{
					captureDiaryRun(id, Math.round(pendingDiaryDurationSeconds * 1000), boss);
					pendingDiaryActivityId = null;
					pendingDiaryDurationSeconds = null;
				}
			}
			return;
		}

		Matcher duration = OBSERVED_ACTIVITY_DURATION.matcher(message);
		if (!duration.matches()) return;
		Double seconds = parseTime(duration.group("time"));
		if (seconds == null) return;
		if (pendingDiaryActivityId != null && now <= pendingDiaryActivityUntil)
		{
			captureDiaryRun(pendingDiaryActivityId, Math.round(seconds * 1000), message);
			pendingDiaryActivityId = null;
			pendingDiaryDurationSeconds = null;
		}
		else
		{
			pendingDiaryDurationSeconds = seconds;
			pendingDiaryDurationUntil = now + 2_000L;
		}
	}

	private static int parseTeamSize(String value)
	{
		Matcher matcher = Pattern.compile("[0-9]+").matcher(value == null ? "" : value);
		return matcher.find() ? Integer.parseInt(matcher.group()) : 0;
	}

	private void captureDiaryRun(String activityId, Object result, String sourceName)
	{
		if (result instanceof Number && !diaryMeetsEasy(activityId, ((Number) result).longValue())) return;
		Map<String, Object> diary = new HashMap<>();
		diary.put("activityId", activityId);
		diary.put("value", result);
		Map<String, Object> details = new HashMap<>();
		details.put("pvmDiary", diary);
		details.put("autoSubmit", true);
		ZweetnekkenModels.Source source = new ZweetnekkenModels.Source();
		source.type = "raid";
		source.name = sourceName;
		pipeline.capture("diary", activityId + '|' + result, source, null, details, true);
	}

	private boolean captureArchiveCompletion(String message)
	{
		Matcher matcher = ARCHIVE_COMPLETION.matcher(message);
		if (!matcher.matches()) return false;
		Integer teamSize = parties.confirmedRaidTeamSize("The Fractured Archive");
		if (teamSize == null || teamSize < 1 || teamSize > 8)
		{
			log.warn("Fractured Archive completion observed without a named raid roster; skipping unscaled PB");
			return true;
		}
		Double seconds = parseTime(matcher.group("time"));
		Double best = matcher.group("best") == null ? seconds : parseTime(matcher.group("best"));
		if (seconds == null || best == null) return true;		// RuneLite does not yet write personalbest config for this raid. The game
		// prints the existing PB on slower runs too, allowing catalogue recovery.
		ZweetnekkenModels.PbRecord record = pbRecordFromKey("the fractured archive " + teamSize + " players", best);
		String key = recordKey(record);
		Double previous = known.get(key);
		if (previous == null || best < previous)
		{
			known.put(key, best);
			sync(java.util.Collections.singletonList(record), false, matcher.group("best") == null, previous, false);
		}
		return true;
	}

	@Subscribe public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.JOURNALSCROLL)
		{
			journalLoaded = true;
		}
		Scoreboard scoreboard = scoreboardFor(event.getGroupId()); if (scoreboard != null) pendingScoreboard = scoreboard;
	}

	@Subscribe public void onGameTick(GameTick event)
	{		if (pendingScoreboard != null) { Scoreboard scoreboard = pendingScoreboard; pendingScoreboard = null; readScoreboard(scoreboard); }
		if (!journalLoaded) return; journalLoaded = false;
		Widget title = client.getWidget(InterfaceID.Journalscroll.TITLE);
		String owner = title == null ? null : Text.removeTags(title.getText()).replaceFirst("^The Exploits of ", "").trim();
		if (owner != null && !owner.isEmpty() && client.getLocalPlayer() != null && !owner.equalsIgnoreCase(client.getLocalPlayer().getName()) && !owner.equalsIgnoreCase("Counters"))
		{
			return;
		}
		Widget parent = client.getWidget(InterfaceID.Journalscroll.TEXTLAYER);
		Widget[] children = collectChildren(parent);
		if (children.length == 0) return;
		List<ZweetnekkenModels.PbRecord> records = parseAdventureLog(children);
		if (!records.isEmpty())
		{
			for (ZweetnekkenModels.PbRecord record : records)
			{
				String key = recordKey(record);
				if (record.durationMillis != null)
				{
					Double seconds = record.durationMillis / 1000.0;
					known.put(key, seconds);
				}
			}
			String fingerprint = fingerprint(records);
			if (fingerprint.equals(lastFingerprint))
			{
				return;
			}
			lastFingerprint = fingerprint;
			sync(records, true, false, null, true);
		}
	}

	static Widget[] collectChildren(Widget parent)
	{
		if (parent == null) return new Widget[0];
		List<Widget> list = new ArrayList<>();
		addNonNullChildren(list, parent.getStaticChildren());
		addNonNullChildren(list, parent.getDynamicChildren());
		addNonNullChildren(list, parent.getNestedChildren());
		addNonNullChildren(list, parent.getChildren());
		return list.toArray(new Widget[0]);
	}

	private static void addNonNullChildren(List<Widget> list, Widget[] children)
	{
		if (children != null)
		{
			for (Widget child : children)
			{
				if (child != null) list.add(child);
			}
		}
	}

	private void readScoreboard(Scoreboard scoreboard)
	{
		Widget titleWidget = client.getWidget(scoreboard.titleComponentId); Widget pbWidget = client.getWidget(scoreboard.pbComponentId);
		String title = titleWidget == null ? "" : Text.removeTags(titleWidget.getText()); String raw = pbWidget == null ? null : Text.removeTags(pbWidget.getText()).trim();
		Double seconds = raw == null ? null : parseTime(raw); if (!title.toLowerCase(Locale.ROOT).contains(scoreboard.boss.toLowerCase(Locale.ROOT)) || seconds == null) return;
		String key = scoreboard.boss + (title.toLowerCase(Locale.ROOT).contains("awakened") ? " (awakened)" : "");
		Double previous = known.put(key, seconds); if (previous != null && seconds >= previous) return;
		sync(java.util.Collections.singletonList(fromKey(key, seconds)), false, true, previous, false);
	}

	private static Scoreboard scoreboardFor(int groupId)
	{
		switch (groupId)
		{
			case InterfaceID.DUKE_SUCELLUS_SCOREBOARD: return new Scoreboard("Duke Sucellus", InterfaceID.DukeSucellusScoreboard.TITLE_TEXT, InterfaceID.DukeSucellusScoreboard.PBT_CONTENT);
			case InterfaceID.LEVIATHAN_SCOREBOARD: return new Scoreboard("Leviathan", InterfaceID.LeviathanScoreboard.TITLE_TEXT, InterfaceID.LeviathanScoreboard.PBT_CONTENT);
			case InterfaceID.WHISPERER_SCOREBOARD: return new Scoreboard("Whisperer", InterfaceID.WhispererScoreboard.TITLE_TEXT, InterfaceID.WhispererScoreboard.PBT_CONTENT);
			case InterfaceID.VARDORVIS_SCOREBOARD: return new Scoreboard("Vardorvis", InterfaceID.VardorvisScoreboard.TITLE_TEXT, InterfaceID.VardorvisScoreboard.PBT_CONTENT);
			default: return null;
		}
	}

	private static final class Scoreboard
	{
		final String boss; final int titleComponentId; final int pbComponentId;
		Scoreboard(String boss, int titleComponentId, int pbComponentId) { this.boss = boss; this.titleComponentId = titleComponentId; this.pbComponentId = pbComponentId; }
	}

	public void syncAll(boolean force)
	{
		if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null) { status = "Log in om PB's te synchroniseren"; return; }
		String profile = configManager.getRSProfileKey(); if (profile == null) { status = "RuneLite-profiel is niet beschikbaar"; return; }
		List<ZweetnekkenModels.PbRecord> records = new ArrayList<>();
		for (String key : configManager.getRSProfileConfigurationKeys(GROUP, profile, ""))
		{
			Double seconds = configManager.getRSProfileConfiguration(GROUP, key, double.class);
			if (seconds != null) records.add(pbRecordFromKey(key, seconds));
		}
		String fingerprint = fingerprint(records);
		if (!force && fingerprint.equals(lastFingerprint)) { status = "PB's zijn al bijgewerkt"; return; }
		lastFingerprint = fingerprint; sync(records, true, false, null);
	}

	private void sync(List<ZweetnekkenModels.PbRecord> records, boolean bulk, boolean evidence, Double previous)
	{
		sync(records, bulk, evidence, previous, false);
	}

	private void sync(List<ZweetnekkenModels.PbRecord> records, boolean bulk, boolean evidence, Double previous, boolean fromAdventureLog)
	{
		if ((!bulk && records.isEmpty()) || client.getLocalPlayer() == null) return;
		if (fromAdventureLog)
		{
			message("Je adventure log synchroniseren met Zweetnekken…");
		}
		ZweetnekkenModels.PbBulkRequest request = new ZweetnekkenModels.PbBulkRequest(); request.syncId = UUID.randomUUID().toString(); request.capturedAt = Instant.now().toString();
		request.player = new ZweetnekkenModels.PlayerIdentity(); request.player.name = client.getLocalPlayer().getName(); request.player.accountHash = String.valueOf(client.getAccountHash()); request.records.addAll(records);
		status = records.size() + (records.size() == 1 ? " PB synchroniseren…" : " PB's synchroniseren…");
		api.syncPbs(request, bulk, result ->
		{
			status = result.isSuccessful() ? "PB's gesynchroniseerd " + Instant.now() : result.error;
			if (!result.isSuccessful()) log.error(
				"Zweetnekken PB sync failed: player={}, syncId={}, error={}", request.player.name, request.syncId, result.error);

			if (fromAdventureLog)
			{
				clientThread.invokeLater(() ->
				{
					if (result.isSuccessful())
					{
						message("Je adventure log is gesynchroniseerd met Zweetnekken.");
					}
					else
					{
						message("Zweetnekken heeft je adventure log in de wachtrij gezet en probeert het later opnieuw.");
					}
				});
			}
		});
		if (evidence)
		{
			ZweetnekkenModels.PbRecord record = records.get(0);
			Map<String, Object> details = new HashMap<>();
			details.put("record", record);
			details.put("autoSubmit", true);
			if (previous != null) details.put("previousDurationMillis", Math.round(previous * 1000));
			// A personal-best update is recorded separately from loot or other
			// evidence submissions.
			pipeline.capture("personal_best", recordKey(record) + '|' + record.durationMillis, null, null, details, false);
			captureDiaryEvidence(record);
		}
	}

	private void captureDiaryEvidence(ZweetnekkenModels.PbRecord record)
	{
		if (record == null || record.durationMillis == null || record.durationMillis <= 0) return;
		String activityId = diaryActivityId(record);
		if (activityId == null) return;
		long value = record.durationMillis;
		// Do not create evidence that cannot reach even the Easy tier. The backend
		// repeats this check against its canonical catalogue before accepting it.
		if (!diaryMeetsEasy(activityId, value)) return;
		Map<String, Object> diary = new HashMap<>();
		diary.put("activityId", activityId);
		diary.put("value", "tob-solo".equals(activityId) || "hmt-duo".equals(activityId)
			? "completion" : value);
		Map<String, Object> details = new HashMap<>();
		details.put("pvmDiary", diary);
		details.put("record", record);
		details.put("autoSubmit", true);
		pipeline.capture("diary", activityId + '|' + value, null, null, details, true);
	}

	private static String diaryActivityId(ZweetnekkenModels.PbRecord record)
	{
		String activity = record.activity == null ? "" : record.activity.toLowerCase(Locale.ROOT);
		int team = record.teamSize == null ? 0 : record.teamSize;
		boolean challengeMode = "challenge_mode".equalsIgnoreCase(record.variant);
		if (activity.equals("chambers of xeric"))
		{
			String prefix = challengeMode ? "cox-cm-" : "cox-";
			if (team == 1) return prefix + "solo";
			if (team == 3) return prefix + "trios";
			if (team == 5) return prefix + "5s";
		}
		if (activity.equals("theatre of blood"))
		{
			boolean hmt = "hard".equalsIgnoreCase(record.variant);
			if (hmt)
			{
				if (team == 2) return "hmt-duo";
				if (team == 3) return "hmt-trios";
				if (team == 4) return "hmt-4s";
				if (team == 5) return "hmt-5s";
			}
			else
			{
				if (team == 1) return "tob-solo";
				if (team == 2) return "tob-duo";
				if (team == 3) return "tob-trio";
				if (team == 4) return "tob-4s";
				if (team == 5) return "tob-5s";
			}
		}
		if (activity.equals("inferno")) return "inferno-solo";
		if (activity.equals("tzhaar fight cave")) return "fight-caves-solo";
		if (activity.equals("fortis colosseum")) return "colosseum-solo";
		if (activity.equals("corrupted gauntlet")) return "cg-solo";
		return null;
	}

	private static boolean diaryMeetsEasy(String activityId, long millis)
	{
		switch (activityId)
		{
			case "cox-solo": return millis <= 20 * 60_000L;
			case "cox-trios": return millis <= 17 * 60_000L;
			case "cox-5s": return millis <= 15 * 60_000L;
			case "cox-cm-solo": return millis <= 40 * 60_000L;
			case "cox-cm-trios": return millis <= 30 * 60_000L;
			case "cox-cm-5s": return millis <= 30 * 60_000L;
			case "tob-solo": case "hmt-duo": return true;
			case "tob-duo": return millis <= 28 * 60_000L;
			case "tob-trio": return millis <= 19 * 60_000L;
			case "tob-4s": return millis <= 17 * 60_000L + 30_000L;
			case "tob-5s": return millis <= 15 * 60_000L;
			case "hmt-trios": return millis <= 26 * 60_000L;
			case "hmt-4s": return millis <= 24 * 60_000L;
			case "hmt-5s": return millis <= 22 * 60_000L;
			case "inferno-solo": return millis <= 80 * 60_000L;
			case "fight-caves-solo": return millis <= 29 * 60_000L;
			case "colosseum-solo": return millis <= 30 * 60_000L;
			case "cg-solo": return millis <= 7 * 60_000L;
			default: return false;
		}
	}

	private void message(String text)
	{
		client.addChatMessage(ChatMessageType.CONSOLE, "Zweetnekken", text, "Zweetnekken");
	}

	private void hydrateKnown()
	{
		known.clear(); String profile = configManager.getRSProfileKey(); if (profile == null) return;
		for (String key : configManager.getRSProfileConfigurationKeys(GROUP, profile, ""))
		{
			Double value = configManager.getRSProfileConfiguration(GROUP, key, double.class);
			if (value != null)
			{
				ZweetnekkenModels.PbRecord record = pbRecordFromKey(key, value);
				known.put(recordKey(record), value);
			}
		}
	}

	static String recordKey(ZweetnekkenModels.PbRecord record)
	{
		return record.activity + '|' + record.variant + '|' + record.teamSize + '|' + record.recordType + '|' + record.value;
	}

	static ZweetnekkenModels.PbRecord fromKey(String key, double seconds)
	{
		ZweetnekkenModels.PbRecord record = new ZweetnekkenModels.PbRecord(); record.activity = canonical(key); record.durationMillis = Math.round(seconds * 1000);
		Matcher team = TEAM.matcher(key);
		if (team.find()) record.teamSize = Integer.parseInt(team.group(1));
		else if (key.toLowerCase(Locale.ROOT).contains("solo")) record.teamSize = 1;
		String lower = key.toLowerCase(Locale.ROOT);
		if (lower.contains("awakened")) record.variant = "awakened";
		else if (lower.contains("challenge")) record.variant = "challenge_mode";
		else if (lower.contains("expert")) record.variant = "expert";
		else if (lower.contains("entry")) record.variant = "entry";
		else if (lower.contains("hard")) record.variant = "hard";
		return record;
	}

	/**
	 * Normalizes RuneLite's raw personal-best config key to the API's PB shape.
	 * Unlike the Adventure Log, the personal-best config uses internal labels.
	 * Normalize these labels while preserving raid mode and team size.
	 */
	static ZweetnekkenModels.PbRecord pbRecordFromKey(String key, double seconds)
	{
		ZweetnekkenModels.PbRecord record = fromKey(key, seconds);
		String lower = key == null ? "" : key.toLowerCase(Locale.ROOT);
		if (lower.equals("sol heredit")) record.activity = "Fortis Colosseum";
		else if (lower.equals("tztok-jad")) record.activity = "TzHaar Fight Cave";
		else if (lower.equals("tzkal-zuk")) record.activity = "Inferno";
		else if (lower.equals("corrupted gauntlet")) record.activity = "Corrupted Gauntlet";
		else if (lower.equals("gauntlet")) record.activity = "Gauntlet";
		else if (lower.equals("hueycoatl")) record.activity = "Hueycoatl";
		if (lower.equals("sol heredit") || lower.equals("fortis colosseum")
			|| lower.equals("tztok-jad") || lower.equals("tzhaar fight cave")
			|| lower.equals("tzkal-zuk") || lower.equals("inferno")
			|| lower.equals("corrupted gauntlet") || lower.equals("the corrupted gauntlet"))
			record.teamSize = 1;

		if (lower.startsWith("chambers of xeric")) record.activity = "Chambers of Xeric";
		else if (lower.startsWith("theatre of blood")) record.activity = "Theatre of Blood";
		else if (lower.startsWith("tombs of amascut")) record.activity = "Tombs of Amascut";
		else if (isFracturedArchiveKey(lower)) record.activity = "The Fractured Archive";
		if (lower.contains("challenge mode")) record.variant = "challenge_mode";
		else if (lower.contains("hard mode")) record.variant = "hard";
		else if (lower.contains("entry mode")) record.variant = "entry";
		if (record.teamSize == null && lower.matches(".*\\bsolo$")) record.teamSize = 1;
		return record;
	}

	private static final Set<String> SECTION_CATEGORIES = new HashSet<>(java.util.Arrays.asList("minigames", "bosses", "skilling bosses", "raids", "other"));

	static List<ZweetnekkenModels.PbRecord> parseAdventureLog(Widget[] children)
	{
		List<ZweetnekkenModels.PbRecord> result = new ArrayList<>(); String heading = null;
		List<String> lines = new ArrayList<>();
		if (children != null)
		{
			for (Widget child : children)
			{
				if (child == null || child.getText() == null) continue;
				String text = Text.removeTags(child.getText()).replace('\u00A0', ' ').trim();
				if (!text.isEmpty()) lines.add(text);
			}
		}
		for (int i = 0; i < lines.size(); i++)
		{
			String current = lines.get(i);
			if (i + 1 < lines.size())
			{
				String next = lines.get(i + 1);
				if (next.matches("-|[0-9:]+(?:\\.[0-9]+)?") && (current.endsWith(":") || current.startsWith("Fastest") || current.contains(":")))
				{
					lines.set(i, current + " " + next);
					lines.remove(i + 1);
				}
			}
		}
		for (String line : lines)
		{
			if (line == null || line.isEmpty()) { continue; }
			String lower = line.toLowerCase(Locale.ROOT);
			if (SECTION_CATEGORIES.contains(lower))
			{
				heading = null;
				continue;
			}
			if (line.endsWith(":"))
			{
				heading = line.substring(0, line.length() - 1).trim();
				continue;
			}
			Matcher matcher = RECORD.matcher(line);
			if (matcher.matches())
			{
				if (heading == null || "-".equals(matcher.group("value"))) continue;
				Double seconds = parseTime(matcher.group("value"));
				if (seconds == null) continue;
				ZweetnekkenModels.PbRecord record = new ZweetnekkenModels.PbRecord();
				record.activity = canonical(heading);
				record.durationMillis = Math.round(seconds * 1000);
				String descriptor = matcher.group("descriptor");
				record.recordType = descriptor.toLowerCase(Locale.ROOT).contains("room") ? "room"
					: descriptor.toLowerCase(Locale.ROOT).contains("wave") ? "wave" : "overall";
				Matcher team = TEAM.matcher(descriptor);
				if (team.find()) record.teamSize = Integer.parseInt(team.group(1));
				else if (descriptor.toLowerCase(Locale.ROOT).contains("solo") && hasTeamSizeRecords(heading)) record.teamSize = 1;

				String combined = (heading + " " + descriptor).toLowerCase(Locale.ROOT);
				if (combined.contains("awakened")) record.variant = "awakened";
				else if (combined.contains("challenge")) record.variant = "challenge_mode";
				else if (combined.contains("expert")) record.variant = "expert";
				else if (combined.contains("entry")) record.variant = "entry";
				else if (combined.contains("hard")) record.variant = "hard";

				result.add(record);
			}
			else if (line.contains(":") || lower.contains("kill score of"))
			{
				parseStatLine(heading, line, result);
			}
			else
			{
				heading = line.trim();
			}
		}
		return result;
	}

	/**
	 * RuneLite describes ordinary single-player boss times as "solo" too. Those
	 * profile records have no team size, so assigning {@code 1} here gives the
	 * Adventure Log upload a different identity from the login upload.
	 */
	private static boolean hasTeamSizeRecords(String activity)
	{
		if (activity == null) return false;
		String lower = activity.toLowerCase(Locale.ROOT);
		return lower.startsWith("chambers of xeric")
			|| lower.startsWith("theatre of blood")
			|| lower.startsWith("tombs of amascut")
			|| isFracturedArchiveKey(lower)
			|| lower.contains("nightmare");
	}

	private static boolean isFracturedArchiveKey(String lower)
	{
		return lower.equals("the fractured archive") || lower.startsWith("the fractured archive ")
			|| lower.equals("fractured archive") || lower.startsWith("fractured archive ");
	}

	static void parseStatLine(String heading, String line, List<ZweetnekkenModels.PbRecord> result)
	{
		if (line == null || line.trim().isEmpty()) return;
		String[] parts = line.split(",\\s*(?=[A-Za-z0-9\\s-]+:|with a kill)");
		for (String part : parts)
		{
			part = part.trim();
			if (part.isEmpty()) continue;
			if (part.contains(":"))
			{
				String[] kv = part.split(":", 2);
				String key = kv[0].trim();
				String val = kv[1].trim();

				ZweetnekkenModels.PbRecord record = new ZweetnekkenModels.PbRecord();
				record.activity = canonical(heading != null ? heading : key);
				record.value = val;

				String keyLower = key.toLowerCase(Locale.ROOT);
				if (keyLower.equals("wins")) { record.recordType = "wins"; record.count = parseLongSafe(val); }
				else if (keyLower.equals("losses")) { record.recordType = "losses"; record.count = parseLongSafe(val); }
				else if (keyLower.equals("rank")) { record.recordType = "rank"; record.count = parseLongSafe(val); }
				else if (keyLower.equals("kills")) { record.recordType = "kills"; record.count = parseLongSafe(val); }
				else if (keyLower.contains("gamble")) { record.recordType = "gambles"; record.count = parseLongSafe(val); }
				else if (keyLower.contains("spirits rested")) { record.recordType = "count"; record.count = parseLongSafe(val); }
				else if (java.util.Arrays.asList("beginner", "easy", "medium", "hard", "elite", "master").contains(keyLower))
				{
					record.activity = canonical(heading != null ? heading : "Treasure Trails");
					record.variant = keyLower;
					record.recordType = "clues";
					record.count = parseLongSafe(val);
				}
				else
				{
					record.recordType = keyLower.replaceAll("[^a-z0-9_]+", "_");
					record.count = parseLongSafe(val);
				}
				result.add(record);
			}
			else if (part.toLowerCase(Locale.ROOT).contains("kill score of"))
			{
				Matcher m = Pattern.compile("kill score of\\s+([0-9,]+)", Pattern.CASE_INSENSITIVE).matcher(part);
				if (m.find())
				{
					ZweetnekkenModels.PbRecord record = new ZweetnekkenModels.PbRecord();
					record.activity = canonical(heading != null ? heading : "Order of the White Knights");
					record.recordType = "score";
					record.value = m.group(1);
					record.count = parseLongSafe(m.group(1));
					result.add(record);
				}
			}
		}
	}

	private static Long parseLongSafe(String text)
	{
		if (text == null) return null;
		try { return Long.parseLong(text.replaceAll("[^0-9]", "")); }
		catch (NumberFormatException e) { return null; }
	}

	static Double parseTime(String value)
	{
		try { String[] p = value.split(":"); if (p.length == 2) return Integer.parseInt(p[0]) * 60 + Double.parseDouble(p[1]); if (p.length == 3) return Integer.parseInt(p[0]) * 3600 + Integer.parseInt(p[1]) * 60 + Double.parseDouble(p[2]); return Double.parseDouble(value); }
		catch (NumberFormatException e) { return null; }
	}

	private static String canonical(String key)
	{
		if (key.equalsIgnoreCase("fractured archive") || key.equalsIgnoreCase("the fractured archive"))
			return "The Fractured Archive";
		String lower = key.toLowerCase(Locale.ROOT); if (lower.equals("the leviathan") || lower.equals("levi")) return "Leviathan"; if (lower.equals("duke")) return "Duke Sucellus"; if (lower.equals("the whisperer") || lower.equals("whisp")) return "Whisperer"; if (lower.equals("vard")) return "Vardorvis"; return key;
	}

	private static String fingerprint(List<ZweetnekkenModels.PbRecord> records)
	{
		TreeMap<String, Long> sorted = new TreeMap<>();
		for (ZweetnekkenModels.PbRecord r : records)
		{
			long val = r.durationMillis != null ? r.durationMillis : (r.count != null ? r.count : 0L);
			sorted.put(r.activity + '|' + r.variant + '|' + r.teamSize + '|' + r.recordType + '|' + r.value, val);
		}
		return sorted.toString();
	}
}

