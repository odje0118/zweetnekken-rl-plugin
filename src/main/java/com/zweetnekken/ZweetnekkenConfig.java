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

	@ConfigSection(name = "Accountkoppeling", description = "Verbindt met de Zweetnekken-backend. De plugin verstuurt je RSN/account-ID, loot- en eventgegevens, groeps- en claninformatie, persoonlijke records, skillniveaus en XP, en screenshots van de volledige client. Screenshots kunnen zichtbare game-interface en chat bevatten en ter beoordeling worden doorgestuurd naar de Zweetnekken-Discord.", position = 0)
	String AUTH = "authentication";

	@ConfigSection(name = "Bewijs", description = "Instellingen voor screenshots en uploads", position = 1)
	String EVIDENCE = "evidence";

	@ConfigSection(name = "Uiterlijk", description = "Pas het Zweetnekken-zijpaneel aan", position = 2)
	String APPEARANCE = "appearance";

	@ConfigSection(name = "Eventmeldingen", description = "Aankondigingen van clan-events", position = 3)
	String EVENT_ALERTS = "eventAlerts";

	@ConfigSection(name = "Bingo-overlay", description = "Overlay voor het actieve bingo-event", position = 4)
	String BINGO_OVERLAY = "bingoOverlay";

	@ConfigItem(keyName = "authenticationCode", name = "Permanente koppelcode",
		description = "Gebruik /linkplugin in de Zweetnekken-Discord en plak de code hier. Hiermee verbind je met de Zweetnekken-backend. Je RSN/accountgegevens, loot en events, groeps- en claninformatie, PB's, skillniveaus en XP, en screenshots worden verstuurd. Screenshots kunnen ter beoordeling worden doorgestuurd naar de clan-Discord.",
		section = AUTH, position = 0, secret = true)
	default String authenticationCode() { return ""; }

	@ConfigItem(keyName = "animatedBanner", name = "Geanimeerde banner",
		description = "Toon de geanimeerde clanbanner als er een GIF beschikbaar is. Schakel dit uit voor de statische banner.",
		section = APPEARANCE, position = 0)
	default boolean animatedBanner() { return true; }

	@ConfigItem(keyName = "includeSidebar", name = "Zijbalk meenemen",
		description = "Neem de RuneLite-zijbalk op in bewijsscreenshots",
		section = EVIDENCE, position = 0)
	default boolean includeSidebar() { return true; }

	@ConfigItem(keyName = "hidePrivateMessages", name = "Privéberichten verbergen",
		description = "Verberg tijdelijk gesplitste privéchat tijdens het maken van bewijs",
		section = EVIDENCE, position = 1)
	default boolean hidePrivateMessages() { return true; }

	@ConfigItem(keyName = "retentionDays", name = "Bewaartermijn van bewijs",
		description = "Aantal dagen dat succesvol geüpload bewijs lokaal wordt bewaard",
		section = EVIDENCE, position = 2)
	default int retentionDays() { return 30; }

	@ConfigItem(keyName = "autoSubmitEnabled", name = "Alles automatisch inzenden",
		description = "Dien alle soorten bewijs automatisch in na het uploaden",
		section = EVIDENCE, position = 3)
	default boolean autoSubmitEnabled() { return true; }

	@ConfigItem(keyName = "eventAlerts", name = "Eventmeldingen",
		description = "Ontvang aankondigingen van nieuwe events in de gamechat",
		section = EVENT_ALERTS, position = 0)
	default boolean eventAlerts() { return true; }

	@ConfigItem(keyName = "eventAlertOverlay", name = "Eventmeldingen als overlay",
		description = "Toon aankondigingen van nieuwe events als overlay in de game",
		section = EVENT_ALERTS, position = 1)
	default boolean eventAlertOverlay() { return true; }

	@ConfigItem(keyName = "eventAlertDuration", name = "Duur van overlay (s)",
		description = "Aantal seconden dat een nieuwe melding in beeld blijft",
		section = EVENT_ALERTS, position = 2)
	default int eventAlertDuration() { return 15; }

	@ConfigItem(keyName = "eventAlertFlash", name = "Overlay laten knipperen",
		description = "Laat de overlay knipperen wanneer er een eventmelding actief is",
		section = EVENT_ALERTS, position = 3)
	default boolean eventAlertFlash() { return false; }

	@ConfigItem(keyName = "eventAlertFlashColor", name = "Knipperkleur",
		description = "Kies de achtergrondkleur voor het knipperen",
		section = EVENT_ALERTS, position = 4)
	default Color eventAlertFlashColor() { return new Color(175, 215, 255, 240); }

	@ConfigItem(keyName = "eventAlertColor", name = "Kleur van eventmeldingen",
		description = "Kies de tekstkleur voor eventmeldingen",
		section = EVENT_ALERTS, position = 5)
	default Color eventAlertColor() { return new Color(0, 130, 235); }

	@ConfigItem(keyName = "bingoOverlayEnabled", name = "Bingo-overlay",
		description = "Toon de overlay van het actieve bingo-event",
		section = BINGO_OVERLAY, position = 0)
	default boolean bingoOverlayEnabled() { return true; }

	@ConfigItem(keyName = "bingoOverlayScreenshotOnly", name = "Alleen op screenshots",
		description = "Toon de bingo-overlay alleen op gemaakte bewijsscreenshots",
		section = BINGO_OVERLAY, position = 2)
	default boolean bingoOverlayScreenshotOnly() { return false; }

	@ConfigItem(keyName = "bingoOverlayTextColor", name = "Tekstkleur",
		description = "Kies de tekstkleur van de actieve event-overlay",
		section = BINGO_OVERLAY, position = 1)
	default Color bingoOverlayTextColor() { return Color.WHITE; }


}
