package com.zweetnekken.pb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DiaryChatTemplateTest
{
	@Test
	void matchesConfiguredDoomCompletionAndNormalDelve()
	{
		DiaryChatTemplate completion = DiaryChatTemplate.compile("Level {wave} Complete!", true);
		DiaryChatTemplate delve = DiaryChatTemplate.compile("Delve level {wave} duration: {time}{rest}", true);
		assertEquals("12", completion.result("Level 12 Complete!"));
		assertEquals("7", delve.result("Delve level 7 duration: 1:56"));
		assertNull(completion.result("Level 12 entered!"));
		assertNull(delve.result("Delve level 8+ (10) duration: 1:56"));
	}

	@Test
	void readsDeepDelveFromConfiguredChatTemplate()
	{
		DiaryChatTemplate rule = DiaryChatTemplate.compile("Delve level 8+ ({wave}) duration: {time}{rest}", true);
		assertEquals("10", rule.result("Delve level 8+ (10) duration: 1:56, Personal best: 0:53"));
		assertEquals("11", rule.result("Delve level 8+ (11) duration: 1:59"));
		assertNull(rule.result("Delve level 8+ (10) entered"));
		assertNull(rule.result("Delve level 88 (10) duration: 1:56"));
	}

	@Test
	void keepsTheObservedTimeSeparateFromThePersonalBest()
	{
		DiaryChatTemplate rule = DiaryChatTemplate.compile("Colosseum duration: {time}{rest}", false);
		assertEquals("19:31.2", rule.result("Colosseum duration: 19:31.2. Personal best: 18:00"));
		assertNull(rule.result("Colosseum duration: 19:90"));
		assertNull(rule.result("Other activity duration: 19:31.2"));
		assertEquals("19:31.2", rule.result("Colosseum duration: 19:31.2. New personal best by 0:58."));
		assertNull(rule.result("You beat your previous personal best by 0:58."));
		assertNull(rule.result("Wave 12 completed! Wave duration: 0:58."));
	}

	@Test
	void rejectsUnboundedAndAmbiguousTemplateSyntax()
	{
		assertNull(DiaryChatTemplate.compile("{time}", false));
		assertNull(DiaryChatTemplate.compile("Arena duration: {rest}{time}", false));
		assertNull(DiaryChatTemplate.compile("Arena duration: {wave}", false));
		assertNull(DiaryChatTemplate.compile("Arena duration: {time} vs {time}", false));
	}
}
