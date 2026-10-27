/*
 * Portions adapted from the Llama Club loot notification and tracking code.
 * See LICENSES/llama-LICENSE.txt.
 */
package com.zweetnekken.events;

import com.zweetnekken.evidence.EventPipeline;
import com.zweetnekken.model.ZweetnekkenModels;
import com.zweetnekken.service.BingoService;
import com.zweetnekken.service.LootEligibility;
import com.zweetnekken.service.PartyTracker;
import com.zweetnekken.service.RulesService;
import com.zweetnekken.service.ZweetnekkenDataService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class LootEventListener
{
	private static final Logger log = LoggerFactory.getLogger(LootEventListener.class);
	@Inject private ItemManager itemManager;
	@Inject private RulesService rules;
	@Inject private ZweetnekkenDataService data;
	@Inject private EventPipeline pipeline;
	@Inject private BingoService bingo;
	@Inject private PartyTracker parties;

	/** Yama loot is published by RuneLite as ServerNpcLoot rather than NpcLootReceived. */
	@Subscribe public void onServerNpcLoot(ServerNpcLoot event)
	{
		NPCComposition composition = event.getComposition();
		if (composition == null || composition.getId() != NpcID.YAMA) return;
		dispatch(event.getItems(), composition.getName(), composition.getId(), "npc");
	}

	@Subscribe public void onNpcLootReceived(NpcLootReceived event)
	{
		NPC npc = event.getNpc();
		dispatch(event.getItems(), npc == null ? "Unknown" : npc.getName(), npc == null ? null : npc.getId(), "npc");
	}

	@Subscribe public void onLootReceived(LootReceived event)
	{
		if (event.getType() == LootRecordType.PLAYER) return;
		dispatch(event.getItems(), event.getName(), null, event.getType().name().toLowerCase());
	}

	private void dispatch(Collection<ItemStack> stacks, String sourceName, Integer sourceId, String sourceType)
	{
		if (parties != null) parties.observeSource(sourceName);
		bingo.observeSource(sourceId, sourceName);
		List<ZweetnekkenModels.Item> allItems = new ArrayList<>();
		List<ZweetnekkenModels.Item> eligible = new ArrayList<>();
		for (ItemStack stack : stacks)
		{
			ItemComposition composition = itemManager.getItemComposition(stack.getId());
			if (composition == null) continue;
			ZweetnekkenModels.Item item = new ZweetnekkenModels.Item(); item.itemId = stack.getId(); item.name = composition.getName();
			item.quantity = stack.getQuantity(); item.unitGeValue = itemManager.getItemPrice(stack.getId());
			item.totalGeValue = item.unitGeValue * item.quantity; item.tradeable = composition.isTradeable(); item.stackable = composition.isStackable();
			allItems.add(item);
			if (LootEligibility.shouldCapture(item, rules.current())) eligible.add(item);
		}
		List<ZweetnekkenModels.Item> bingoItems = bingo.matchingItems(sourceId, sourceName, allItems);
		if (eligible.isEmpty() && bingoItems.isEmpty()) return;
		ZweetnekkenModels.Source source = new ZweetnekkenModels.Source(); source.type = sourceType; source.id = sourceId; source.name = sourceName;
		// Use the item as the key so a collection-log notification for the same drop does not
		// create a second loot submission moments after RuneLite's loot event.
		if (!eligible.isEmpty())
		{
			if (data.canSubmitLoot())
			{
				capture("loot", eligible, source);
			}
			else
			{
				// Trialists can use other plugin features, but ordinary drops must not
				// enter the outbox or Discord review queue before promotion.
				log.debug("Skipping ordinary loot capture: current Zweetnekken rank does not allow loot submissions");
			}
		}
		if (!bingoItems.isEmpty()) captureBingo(bingoItems, source);
	}

	private void capture(String type, List<ZweetnekkenModels.Item> items, ZweetnekkenModels.Source source)
	{
		ZweetnekkenModels.Item first = items.get(0);
		String key = first.name == null ? String.valueOf(first.itemId)
			: first.name.trim().toLowerCase(java.util.Locale.ROOT);
		Map<String, Object> details = new LinkedHashMap<>();
		if (first.name != null) details.put("itemName", first.name);
		boolean individualAward = items.stream().anyMatch(item ->
			PartyTracker.isIndividualRaidReward(source.name, item.name));
		if (individualAward) details.put("individualAward", true);
		// Only ordinary loot at the clan's value threshold is eligible for automatic
		// submission; EventPipeline additionally requires the encounter to be solo.
		if (individualAward
			|| items.stream().anyMatch(item -> LootEligibility.isAlwaysSubmit(item.name))
			|| items.stream().anyMatch(item -> LootEligibility.meetsMinimumValue(item, rules.current())))
			details.put("autoSubmit", true);
		pipeline.capture(type, key, source, items, details, true);
	}

	private void captureBingo(List<ZweetnekkenModels.Item> items, ZweetnekkenModels.Source source)
	{
		ZweetnekkenModels.Item first = items.get(0);
		String key = first.name == null ? String.valueOf(first.itemId)
			: first.name.trim().toLowerCase(java.util.Locale.ROOT);
		Map<String, Object> details = new LinkedHashMap<>();
		if (first.name != null) details.put("itemName", first.name);
		bingo.decorateDetails(details);
		pipeline.capture(bingo.eventType(), key, source, items, details, true, bingo.rulesVersion(),
			bingo.screenshotRequired(), bingo.finalizeSubmission());
	}
}
