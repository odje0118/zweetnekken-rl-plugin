package com.zweetnekken.evidence;

import com.zweetnekken.model.ZweetnekkenModels;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PvmDiaryTimeTest
{
	@Test
	void requiresStrictlyFasterTimeForTheSameActivity()
	{
		ZweetnekkenModels.PvmDiaryStatus status = new ZweetnekkenModels.PvmDiaryStatus();
		ZweetnekkenModels.PvmDiaryActivity solo = new ZweetnekkenModels.PvmDiaryActivity();
		solo.id = "cox-solo";
		solo.bestResult = new ZweetnekkenModels.PvmDiaryBestResult();
		solo.bestResult.value = 1_110_000L;
		status.activities.add(solo);

		assertTrue(PvmDiaryTime.improvesPublishedDiaryTime("cox-solo", 1_109_999L, status));
		assertFalse(PvmDiaryTime.improvesPublishedDiaryTime("cox-solo", 1_110_000L, status));
		assertFalse(PvmDiaryTime.improvesPublishedDiaryTime("cox-solo", 1_110_001L, status));
		assertTrue(PvmDiaryTime.improvesPublishedDiaryTime("cox-trios", 1_000_000L, status));
	}

	@Test
	void allowsFirstQualifyingTimeWithoutAPublishedBest()
	{
		assertTrue(PvmDiaryTime.improvesPublishedDiaryTime("cox-solo", 1_200_000L, null));
		assertTrue(PvmDiaryTime.improvesPublishedDiaryTime(
			"cox-solo", 1_200_000L, new ZweetnekkenModels.PvmDiaryStatus()));
	}

	@Test
	void comparesLocalEvidenceWithinTheSameAccountAndActivity()
	{
		ZweetnekkenModels.EventEnvelope run = new ZweetnekkenModels.EventEnvelope();
		run.player = new ZweetnekkenModels.PlayerIdentity();
		run.player.accountHash = "123";
		ZweetnekkenModels.EventEnvelope previous = new ZweetnekkenModels.EventEnvelope();
		previous.eventType = "diary";
		previous.player = new ZweetnekkenModels.PlayerIdentity();
		previous.player.accountHash = "123";
		previous.details.put("pvmDiary", Map.of("activityId", "cox-solo", "value", 1_110_000.0));

		assertTrue(PvmDiaryTime.hasFasterOrEqualDiaryTime(run, previous, "cox-solo", 1_110_000L));
		assertTrue(PvmDiaryTime.hasFasterOrEqualDiaryTime(run, previous, "cox-solo", 1_110_001L));
		assertFalse(PvmDiaryTime.hasFasterOrEqualDiaryTime(run, previous, "cox-solo", 1_109_999L));
		assertFalse(PvmDiaryTime.hasFasterOrEqualDiaryTime(run, previous, "cox-trios", 1_110_001L));
		previous.player.accountHash = "456";
		assertFalse(PvmDiaryTime.hasFasterOrEqualDiaryTime(run, previous, "cox-solo", 1_110_001L));
	}
}
