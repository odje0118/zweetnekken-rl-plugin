package com.zweetnekken.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ZweetnekkenPanelTest
{
	@Test
	void formatsLargeXpScoresCompactly()
	{
		assertEquals("1.6M XP", CompetitionScoreFormatter.format(1_624_348, "XP", "1,624,348 XP"));
		assertEquals("980K XP", CompetitionScoreFormatter.format(980_000, "XP", "980,000 XP"));
		assertEquals("999 XP", CompetitionScoreFormatter.format(999, "XP", "999 XP"));
	}

	@Test
	void keepsNonXpCompetitionScoresUnchanged()
	{
		assertEquals("392 KC", CompetitionScoreFormatter.format(392, "KC", "392 KC"));
	}
}
