package com.zweetnekken.service;


import com.zweetnekken.ZweetnekkenConfig;
import com.zweetnekken.api.ZweetnekkenApiClient;
import com.zweetnekken.model.ZweetnekkenModels;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class BingoService
{
	private final ZweetnekkenApiClient api;
	private final ZweetnekkenDataService data;
	@Inject private ZweetnekkenConfig config;
	private volatile ZweetnekkenModels.BingoEvent event;
	private volatile ZweetnekkenModels.BingoRules rules;
	private volatile Set<Integer> itemIds = java.util.Collections.emptySet();
	private volatile Set<Integer> bossIds = java.util.Collections.emptySet();
	private volatile long lastBossSourceAt;

	@Inject
	public BingoService(ZweetnekkenApiClient api, ZweetnekkenDataService data)
	{
		this.api = api;
		this.data = data;
	}

	public ZweetnekkenModels.BingoEvent current() { return event; }
	public ZweetnekkenModels.BingoRules currentRules() { return rules; }
	public ZweetnekkenModels.BingoTeam currentTeam()
	{
		ZweetnekkenModels.BingoEvent active = event;
		ZweetnekkenModels.Profile profile = data.profile();
		if (active == null || active.teamsByDiscordId == null || profile == null || profile.member == null
			|| profile.member.discordId == null) return null;
		return active.teamsByDiscordId.get(profile.member.discordId.trim());
	}

	public void refresh()
	{
		api.getBingo(result ->
		{
			if (!result.isSuccessful())
			{
				if (result.statusCode == 401 || result.statusCode == 403) clear();
				return;
			}
			if (result.value == null || !result.value.active)
			{
				clear();
				return;
			}
			event = result.value;
		});
		api.getBingoRules(result ->
		{
			if (!result.isSuccessful())
			{
				if (result.statusCode == 401 || result.statusCode == 403) clearRules();
				return;
			}
			if (result.value == null) { clearRules(); return; }
			itemIds = immutableSet(result.value.itemIds);
			bossIds = immutableSet(result.value.bossIds);
			rules = result.value;
		});
	}

	public void clear()
	{
		event = null;
		clearRules();
	}

	private void clearRules()
	{
		rules = null;
		itemIds = java.util.Collections.emptySet();
		bossIds = java.util.Collections.emptySet();
		lastBossSourceAt = 0;
	}

	public List<ZweetnekkenModels.Item> matchingItems(Integer bossId, List<ZweetnekkenModels.Item> items)
	{
		return matchingItems(bossId, null, items);
	}

	public List<ZweetnekkenModels.Item> matchingItems(Integer bossId, String sourceName, List<ZweetnekkenModels.Item> items)
	{
		ZweetnekkenModels.BingoRules activeRules = rules;
		if (event == null || activeRules == null || activeRules.triggers == null || items == null)
			return java.util.Collections.emptyList();
		Set<ZweetnekkenModels.Item> matches = new LinkedHashSet<>();
		for (ZweetnekkenModels.Item item : items)
			if (item != null && activeRules.triggers.matchingItem && itemIds.contains(item.itemId)) matches.add(item);
		if (activeRules.triggers.ordinaryBossLoot && isBossSource(bossId, sourceName)) matches.addAll(items);
		return new ArrayList<>(matches);
	}

	public boolean shouldCaptureCollectionLog(List<ZweetnekkenModels.Item> items)
	{
		ZweetnekkenModels.BingoRules activeRules = rules;
		if (event == null || activeRules == null || activeRules.triggers == null
			|| !activeRules.triggers.bossCollectionLog || items == null) return false;
		if (hasRecentBossSource()) return true;
		for (ZweetnekkenModels.Item item : items) if (item != null && itemIds.contains(item.itemId)) return true;
		return false;
	}

	public boolean shouldCapturePet(Integer bossId, String sourceName, List<Integer> petItemIds)
	{
		ZweetnekkenModels.BingoRules activeRules = rules;
		if (event == null || activeRules == null || activeRules.triggers == null
			|| !activeRules.triggers.bossPet) return false;
		if (isBossSource(bossId, sourceName)) return true;
		if (hasRecentBossSource()) return true;
		if (petItemIds != null) for (Integer itemId : petItemIds) if (itemId != null && itemIds.contains(itemId)) return true;
		return false;
	}

	public boolean isBossSource(Integer bossId, String sourceName)
	{
		if (bossId != null && bossIds.contains(bossId)) return true;
		// Raid reward chests are emitted by RuneLite as named loot events without an NPC ID.
		return BossRegistry.isRaid(sourceName);
	}

	public void observeSource(Integer bossId, String sourceName)
	{
		if (isBossSource(bossId, sourceName)) lastBossSourceAt = System.currentTimeMillis();
	}

	private boolean hasRecentBossSource()
	{
		return lastBossSourceAt > 0 && System.currentTimeMillis() - lastBossSourceAt <= 10_000L;
	}

	public String eventType()
	{
		String type = rules == null || rules.evidence == null ? null : rules.evidence.eventType;
		return type == null || type.isBlank() ? "bingo" : type.trim();
	}

	public boolean screenshotRequired()
	{
		return rules == null || rules.evidence == null || rules.evidence.screenshotRequired;
	}

	public boolean finalizeSubmission()
	{
		return (config == null || config.autoSubmitEnabled())
			&& (rules == null || rules.evidence == null || rules.evidence.finalizeSubmission);
	}

	public String rulesVersion() { return rules == null ? null : rules.rulesVersion; }

	public void decorateDetails(Map<String, Object> details)
	{
		if (details == null) return;
		ZweetnekkenModels.BingoEvent active = event;
		if (active != null && active.eventTitle != null) details.put("eventTitle", active.eventTitle);
		if (rulesVersion() != null) details.put("bingoRulesVersion", rulesVersion());
		ZweetnekkenModels.BingoTeam team = currentTeam();
		if (team != null)
		{
			if (team.teamId != null) details.put("teamId", team.teamId);
			if (team.teamName != null) details.put("teamName", team.teamName);
		}
	}

	private static Set<Integer> immutableSet(List<Integer> values)
	{
		if (values == null || values.isEmpty()) return java.util.Collections.emptySet();
		return java.util.Collections.unmodifiableSet(new HashSet<>(values));
	}
}
