package com.zweetnekken.ui;

import com.zweetnekken.ZweetnekkenPlugin;
import com.zweetnekken.collection.CollectionLogPromptState;
import com.zweetnekken.service.ZweetnekkenDataService;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class CollectionLogSyncOverlay extends OverlayPanel
{
	static final String MESSAGE = "Please open your collection log to sync with Zweetnekken";
	static final String READING_MESSAGE = "Reading your collection log for Zweetnekken...";
	static final String SYNCING_MESSAGE = "Syncing your collection log with Zweetnekken...";
	private static final Color BACKGROUND = new Color(24, 79, 145, 235);
	private static final Color TEXT = new Color(255, 72, 72);
	private final Client client;
	private final ZweetnekkenDataService data;
	private final CollectionLogPromptState promptState;
	private final CollectionLogAutoSync autoSync;

	@Inject
	public CollectionLogSyncOverlay(ZweetnekkenPlugin plugin, Client client, ZweetnekkenDataService data,
		CollectionLogPromptState promptState, CollectionLogAutoSync autoSync)
	{
		super(plugin);
		this.client = client;
		this.data = data;
		this.promptState = promptState;
		this.autoSync = autoSync;
		setPosition(OverlayPosition.TOP_CENTER);
		setPriority(PRIORITY_LOW);
		panelComponent.setBackgroundColor(BACKGROUND);
		panelComponent.setPreferredSize(new Dimension(320, 0));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (client.getGameState() != GameState.LOGGED_IN
			|| data.connection() != ZweetnekkenDataService.Connection.CONNECTED
			|| !data.isCurrentPlayerClanMember()
			|| promptState.hasSyncedForCurrentAccount())
		{
			return null;
		}
		panelComponent.getChildren().clear();
		panelComponent.getChildren().add(TitleComponent.builder().text(autoSync.overlayMessage()).color(TEXT).build());
		return super.render(graphics);
	}
}
