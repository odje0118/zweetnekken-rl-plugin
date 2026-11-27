package com.zweetnekken.pb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DiaryChatMessagesTest
{
	@Test
	void recognizesFormattedDoomCompletion()
	{
		String message = DiaryChatMessages.normalize(" @gre@Level\u00a010\tComplete! @red@");
		assertEquals(10L, DiaryChatMessages.doomLevel(message));
		assertNull(DiaryChatMessages.doomLevel("Level 10"));
		assertNull(DiaryChatMessages.doomLevel("Level 10 reached!"));
	}

	@Test
	void usesTheActualDeepDelveLevelFromCompletionChat()
	{
		assertEquals(10L, DiaryChatMessages.doomLevel(
			DiaryChatMessages.normalize("Delve level 8+ (10) duration: @red@1:56@red@, Personal best: 0:53")));
		assertEquals(8L, DiaryChatMessages.doomLevel("Delve level 8 duration: 1:56."));
		assertNull(DiaryChatMessages.doomLevel("You have failed Mokhaiotl Drift: The Doom finished the first burrow phase."));
		assertNull(DiaryChatMessages.doomLevel("Delve level 8+ (10)"));
	}

	@Test
	void leavesWaveSplitsOutOfSoloDurations()
	{
		assertNull(DiaryChatMessages.soloDuration("Wave 11 completed! Wave duration: 1:05.2"));
		assertNull(DiaryChatMessages.soloDuration("Wave 12 completed! Wave duration: 0:58. Personal best: 0:55."));
	}

	@Test
	void recognizesSoloKillCountsAndTheRunDuration()
	{
		assertEquals("Inferno", DiaryChatMessages.soloCompletionActivity("Your TzKal-Zuk kill count is: 2."));
		assertEquals("TzHaar Fight Cave", DiaryChatMessages.soloCompletionActivity("Your TzTok-Jad kill count is: 3."));
		assertEquals("Fortis Colosseum", DiaryChatMessages.soloCompletionActivity("Your Sol Heredit kill count is: 1."));
		assertEquals("Corrupted Gauntlet", DiaryChatMessages.soloCompletionActivity(
			"Your Corrupted Gauntlet completion count is: 14."));
		assertEquals("Vardorvis", DiaryChatMessages.soloCompletionActivity("Your Vardorvis kill count is: 32."));
		assertEquals("58:30", DiaryChatMessages.soloDuration("Fight duration: 58:30. Personal best: 52:00."));
		assertEquals("51:45", DiaryChatMessages.personalBestDuration("Duration: 51:45. (new personal best)"));
	}
}
