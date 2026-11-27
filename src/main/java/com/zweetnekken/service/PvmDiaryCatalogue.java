package com.zweetnekken.service;

import com.zweetnekken.model.ZweetnekkenModels.PvmDiaryActivity;
import com.zweetnekken.model.ZweetnekkenModels.PvmDiaryCategory;
import com.zweetnekken.model.ZweetnekkenModels.PvmDiaryConfig;
import com.zweetnekken.model.ZweetnekkenModels.PvmDiaryStatus;
import com.zweetnekken.model.ZweetnekkenModels.PvmDiaryTier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

final class PvmDiaryCatalogue
{
	private PvmDiaryCatalogue() {}

	static boolean isActive(PvmDiaryConfig config)
	{
		if (config == null || config.version != 1 || !config.enabled || blank(config.catalogueVersion)
			|| config.categories == null || config.activities == null || config.activities.isEmpty()) return false;

		Set<String> categories = new HashSet<>();
		for (PvmDiaryCategory category : config.categories)
			if (category == null || blank(category.name) || !categories.add(category.name)) return false;

		Set<String> ids = new HashSet<>();
		for (PvmDiaryActivity activity : config.activities)
		{
			if (activity == null || blank(activity.id) || !ids.add(activity.id) || blank(activity.name)
				|| !categories.contains(activity.category) || activity.teamSize < 1
				|| !("time".equals(activity.kind) || "wave".equals(activity.kind) || "completion".equals(activity.kind))
				|| activity.tiers == null || activity.tiers.isEmpty()) return false;
			Set<String> tierNames = new HashSet<>();
			for (PvmDiaryTier tier : activity.tiers)
				if (tier == null || blank(tier.tier) || !tierNames.add(tier.tier) || tier.points < 0
					|| (!"completion".equals(activity.kind) && (tier.target == null || tier.target <= 0))) return false;
		}
		return true;
	}

	static boolean allows(PvmDiaryConfig config, String player)
	{
		if (blank(player)) return false;
		if ("all".equals(config.audience)) return true;
		if (!"testers".equals(config.audience) || config.testers == null) return false;
		String rsn = normalizeRsn(player);
		return config.testers.stream().anyMatch(tester -> !blank(tester) && rsn.equals(normalizeRsn(tester)));
	}

	static boolean sameRsn(String first, String second)
	{
		return !blank(first) && !blank(second) && normalizeRsn(first).equals(normalizeRsn(second));
	}

	private static String normalizeRsn(String name)
	{
		return name.strip().replace('_', ' ').replace('\u00a0', ' ').toLowerCase(Locale.ROOT);
	}

	static PvmDiaryStatus view(PvmDiaryConfig config, PvmDiaryStatus progress)
	{
		if (config == null) return null;
		PvmDiaryStatus view = new PvmDiaryStatus();
		view.catalogueVersion = config.catalogueVersion;
		Map<String, PvmDiaryActivity> saved = new HashMap<>();
		if (progress != null && config.catalogueVersion.equals(progress.catalogueVersion))
		{
			view.fetchedAt = progress.fetchedAt;
			view.delivery = progress.delivery;
			view.pendingSubmissions = progress.pendingSubmissions;
			if (progress.activities != null)
				for (PvmDiaryActivity activity : progress.activities)
					if (activity != null) saved.put(activity.id, activity);
		}
		Map<String, PvmDiaryCategory> categories = new HashMap<>();
		for (PvmDiaryCategory category : config.categories)
		{
			PvmDiaryCategory copy = new PvmDiaryCategory();
			copy.name = category.name;
			categories.put(copy.name, copy);
			view.categories.add(copy);
		}
		for (PvmDiaryActivity definition : config.activities)
		{
			PvmDiaryActivity activity = new PvmDiaryActivity();
			activity.id = definition.id;
			activity.name = definition.name;
			activity.category = definition.category;
			activity.teamSize = definition.teamSize;
			activity.kind = definition.kind;
			PvmDiaryCategory category = categories.get(activity.category);
			PvmDiaryActivity previous = saved.get(activity.id);
			Set<String> completed = new HashSet<>();
			if (previous != null)
			{
				activity.bestResult = previous.bestResult;
				activity.pendingCount = previous.pendingCount;
				if (previous.tiers != null)
					for (PvmDiaryTier tier : previous.tiers)
						if (tier != null && tier.completed) completed.add(tier.tier);
			}
			for (PvmDiaryTier definitionTier : definition.tiers)
			{
				PvmDiaryTier tier = new PvmDiaryTier();
				tier.tier = definitionTier.tier;
				tier.target = definitionTier.target;
				tier.points = definitionTier.points;
				tier.targetLabel = definitionTier.targetLabel;
				tier.completed = completed.contains(tier.tier);
				if (tier.completed)
				{
					activity.earnedPoints += tier.points;
					category.completedTiers++;
					view.completedTiers++;
				}
				else if (activity.nextTarget == null) activity.nextTarget = tier;
				category.totalPoints += tier.points;
				view.totalPoints += tier.points;
				activity.tiers.add(tier);
			}
			category.earnedPoints += activity.earnedPoints;
			view.earnedPoints += activity.earnedPoints;
			category.totalTiers += activity.tiers.size();
			view.totalTiers += activity.tiers.size();
			view.activities.add(activity);
		}
		return view;
	}

	private static boolean blank(String value) { return value == null || value.isBlank(); }
}
