package com.zweetnekken;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(ZweetnekkenConfig.GROUP)
public interface ZweetnekkenConfig extends Config
{
	String GROUP = "zweetnekken";

	@ConfigSection(name = "Authentication", description = "Connect Zweetnekken to Discord", position = 0)
	String AUTH = "authentication";

	@ConfigSection(name = "Evidence", description = "Screenshot and upload behavior", position = 1)
	String EVIDENCE = "evidence";

	@ConfigSection(name = "Appearance", description = "Customize Zweetnekken side panel", position = 2)
	String APPEARANCE = "appearance";

	@ConfigSection(name = "Event Alerts", description = "Clan event announcements", position = 3)
	String EVENT_ALERTS = "eventAlerts";

	@ConfigSection(name = "Bingo Overlay", description = "Active bingo event overlay", position = 4)
	String BINGO_OVERLAY = "bingoOverlay";

	@ConfigItem(keyName = "authenticationCode", name = "Permanent link code",
		description = "Ask staff to use /linkplugin in Zweetnekken Discord, then paste the permanent code here",
		section = AUTH, position = 0, secret = true)
	default String authenticationCode() { return ""; }

	@ConfigItem(keyName = "animatedBanner", name = "Animated banner",
		description = "Show the animated clan banner when a GIF is available; disable to use the static banner",
		section = APPEARANCE, position = 0)
	default boolean animatedBanner() { return true; }

	@ConfigItem(keyName = "includeSidebar", name = "Include sidebar",
		description = "Include the RuneLite sidebar in evidence screenshots",
		section = EVIDENCE, position = 0)
	default boolean includeSidebar() { return true; }

	@ConfigItem(keyName = "hidePrivateMessages", name = "Hide private messages",
		description = "Temporarily hide split private chat while capturing evidence",
		section = EVIDENCE, position = 1)
	default boolean hidePrivateMessages() { return true; }

	@ConfigItem(keyName = "retentionDays", name = "Evidence retention",
		description = "Days to keep successfully uploaded evidence locally",
		section = EVIDENCE, position = 2)
	default int retentionDays() { return 30; }

	@ConfigItem(keyName = "autoSubmitEnabled", name = "Auto-submit everything",
		description = "Automatically submit all evidence types after upload",
		section = EVIDENCE, position = 3)
	default boolean autoSubmitEnabled() { return true; }

	@ConfigItem(keyName = "eventAlerts", name = "Event Alerts",
		description = "Receive new event announcements in game chat",
		section = EVENT_ALERTS, position = 0)
	default boolean eventAlerts() { return true; }

	@ConfigItem(keyName = "eventAlertOverlay", name = "Event Alert Overlay",
		description = "Show new event announcements in an in-game overlay",
		section = EVENT_ALERTS, position = 1)
	default boolean eventAlertOverlay() { return true; }

	@ConfigItem(keyName = "eventAlertDuration", name = "Overlay Duration (s)",
		description = "Duration in seconds to display the overlay when a new alert arrives",
		section = EVENT_ALERTS, position = 2)
	default int eventAlertDuration() { return 15; }

	@ConfigItem(keyName = "eventAlertFlash", name = "Flash Overlay",
		description = "Flash the overlay when an event alert is active",
		section = EVENT_ALERTS, position = 3)
	default boolean eventAlertFlash() { return false; }

	@ConfigItem(keyName = "eventAlertFlashColor", name = "Flash Color",
		description = "Choose the background highlight color when flashing",
		section = EVENT_ALERTS, position = 4)
	default Color eventAlertFlashColor() { return new Color(175, 215, 255, 240); }

	@ConfigItem(keyName = "eventAlertColor", name = "Event Alert Color",
		description = "Choose the color used for event announcements",
		section = EVENT_ALERTS, position = 5)
	default Color eventAlertColor() { return new Color(0, 130, 235); }

	@ConfigItem(keyName = "bingoOverlayEnabled", name = "Bingo Overlay",
		description = "Show the active bingo event overlay",
		section = BINGO_OVERLAY, position = 0)
	default boolean bingoOverlayEnabled() { return true; }

	@ConfigItem(keyName = "bingoOverlayScreenshotOnly", name = "Screenshot only",
		description = "Show the enabled Bingo overlay only in captured evidence screenshots",
		section = BINGO_OVERLAY, position = 2)
	default boolean bingoOverlayScreenshotOnly() { return false; }

	@ConfigItem(keyName = "bingoOverlayTextColor", name = "Text Color",
		description = "Choose the text color used by the active event overlay",
		section = BINGO_OVERLAY, position = 1)
	default Color bingoOverlayTextColor() { return Color.WHITE; }


}
