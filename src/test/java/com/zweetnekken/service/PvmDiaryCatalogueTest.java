package com.zweetnekken.service;

import com.zweetnekken.model.ZweetnekkenModels.PvmDiaryActivity;
import com.zweetnekken.model.ZweetnekkenModels.PvmDiaryCategory;
import com.zweetnekken.model.ZweetnekkenModels.PvmDiaryConfig;
import com.zweetnekken.model.ZweetnekkenModels.PvmDiaryStatus;
import com.zweetnekken.model.ZweetnekkenModels.PvmDiaryTier;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PvmDiaryCatalogueTest
{
	@Test
	void supportsTesterAndPublicRollouts()
	{
		PvmDiaryConfig config = catalogue();
		assertFalse(PvmDiaryCatalogue.isActive(config));
		config.enabled = true;
		assertTrue(PvmDiaryCatalogue.isActive(config));
		assertFalse(PvmDiaryCatalogue.allows(config, "Test Player"));

		config.testers = List.of("Test Player");
		assertTrue(PvmDiaryCatalogue.allows(config, "test_player"));
		assertFalse(PvmDiaryCatalogue.allows(config, "TestPlayer"));
		assertFalse(PvmDiaryCatalogue.allows(config, "Other Player"));

		config.audience = "all";
		assertTrue(PvmDiaryCatalogue.allows(config, "Other Player"));
		assertFalse(PvmDiaryCatalogue.allows(config, null));

		config.audience = "unknown";
		assertFalse(PvmDiaryCatalogue.allows(config, "Test Player"));
		config.version = 2;
		assertFalse(PvmDiaryCatalogue.isActive(config));
	}

	@Test
	void usesConfiguredTargetsAndOnlyMergesProgressFromMatchingVersion()
	{
		PvmDiaryConfig config = catalogue();
		PvmDiaryStatus progress = new PvmDiaryStatus();
		progress.catalogueVersion = "previous";
		PvmDiaryActivity saved = new PvmDiaryActivity();
		saved.id = "test-solo";
		PvmDiaryTier savedTier = new PvmDiaryTier();
		savedTier.tier = "Easy";
		savedTier.target = 70_000L;
		savedTier.points = 50;
		savedTier.completed = true;
		saved.tiers.add(savedTier);
		progress.activities.add(saved);

		assertEquals(0, PvmDiaryCatalogue.view(config, progress).completedTiers);
		progress.catalogueVersion = config.catalogueVersion;
		PvmDiaryStatus view = PvmDiaryCatalogue.view(config, progress);
		assertEquals(1, view.completedTiers);
		assertEquals(10, view.earnedPoints);
		assertEquals(60_000L, view.activities.get(0).tiers.get(0).target.longValue());
		assertFalse(config.activities.get(0).tiers.get(0).completed);
	}

	private static PvmDiaryConfig catalogue()
	{
		PvmDiaryConfig config = new PvmDiaryConfig();
		config.version = 1;
		config.catalogueVersion = "example";
		config.audience = "testers";
		PvmDiaryCategory category = new PvmDiaryCategory();
		category.name = "Test";
		config.categories = List.of(category);
		PvmDiaryActivity activity = new PvmDiaryActivity();
		activity.id = "test-solo";
		activity.name = "Test Solo";
		activity.category = category.name;
		activity.teamSize = 1;
		activity.kind = "time";
		PvmDiaryTier tier = new PvmDiaryTier();
		tier.tier = "Easy";
		tier.target = 60_000L;
		tier.points = 10;
		activity.tiers.add(tier);
		config.activities = List.of(activity);
		return config;
	}
}
