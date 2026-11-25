package com.zweetnekken.ui;

import com.zweetnekken.collection.CollectionLogSyncService;
import com.zweetnekken.model.ZweetnekkenModels;
import com.zweetnekken.service.ZweetnekkenDataService;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

/** Automatically uploads an exact snapshot after the player opens the collection log. */
@Slf4j
@Singleton
public class CollectionLogAutoSync
{
	private static final int CAPTURE_TIMEOUT_TICKS = 20;

	private final Client client;
	private final ClientThread clientThread;
	private final EventBus eventBus;
	private final ZweetnekkenDataService data;
	private final CollectionLogSyncService collectionLogSync;
	private boolean waitingForCapture;
	private boolean uploadInProgress;
	private long attemptedAccountHash;
	private int openedAtTick = Integer.MAX_VALUE;
	private int captureDeadlineTick = -1;

	@Inject
	public CollectionLogAutoSync(Client client, ClientThread clientThread, EventBus eventBus,
		ZweetnekkenDataService data, CollectionLogSyncService collectionLogSync)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.eventBus = eventBus;
		this.data = data;
		this.collectionLogSync = collectionLogSync;
	}

	public void startUp()
	{
		eventBus.register(this);
	}

	public void shutDown()
	{
		eventBus.unregister(this);
		waitingForCapture = false;
		uploadInProgress = false;
	}

	/** Handles a manual request to sync the collection log. */
	void refreshRequested()
	{
		if (!data.isCurrentPlayerClanMember()) return;
		if (uploadInProgress)
		{
			message("Synchronisatie is al bezig.");
			return;
		}
		attemptedAccountHash = 0L;
		if (collectionLogSync.hasCapturedItems())
		{
			startUpload();
		}
		else
		{
			armCapture();
			message("Open de zoekfunctie van je collection log om de gegevens voor Zweetnekken te laden…");
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (!data.isCurrentPlayerClanMember()
			|| event.getGroupId() != InterfaceID.COLLECTION || isPohLog()) return;
		long accountHash = client.getAccountHash();
		if (accountHash == 0L || waitingForCapture || uploadInProgress || attemptedAccountHash == accountHash
			|| collectionLogSync.hasSyncedCurrentAccount()) return;

		armCapture();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!waitingForCapture || uploadInProgress) return;
		if (collectionLogSync.isCaptureReadySince(openedAtTick))
		{
			startUpload();
		}
		else if (client.getTickCount() > captureDeadlineTick)
		{
			waitingForCapture = false;
			log.error("Automatic collection log sync timed out waiting for item rows: openedAtTick={}, currentTick={}",
				openedAtTick, client.getTickCount());
			message("De collection-loggegevens zijn niet geladen. Sluit je collection log en open deze opnieuw.");
		}
	}

	private void armCapture()
	{
		openedAtTick = client.getTickCount();
		captureDeadlineTick = openedAtTick + CAPTURE_TIMEOUT_TICKS;
		waitingForCapture = true;
	}

	private void startUpload()
	{
		if (!data.isCurrentPlayerClanMember())
		{
			waitingForCapture = false;
			return;
		}
		if (data.connection() != ZweetnekkenDataService.Connection.CONNECTED)
		{
			message("Zweetnekken is niet verbonden. Controleer je koppelcode bij de plugininstellingen.");
			return;
		}
		waitingForCapture = false;
		uploadInProgress = true;
		attemptedAccountHash = client.getAccountHash();
		if (collectionLogSync.syncCapturedItems(this::onSyncFinished))
		{
			message("Je collection log synchroniseren met Zweetnekken…");
		}
		else
		{
			uploadInProgress = false;
			attemptedAccountHash = 0L;
			log.error("Automatic collection log sync lost capture readiness before upload");
			message("De collection-loggegevens worden nog geladen. Wacht even en probeer het opnieuw.");
		}
	}

	private void onSyncFinished(boolean successful)
	{
		clientThread.invokeLater(() ->
		{
			uploadInProgress = false;
			if (successful)
			{
				ZweetnekkenModels.Profile profile = data.profile();
				String playerName = profile == null || profile.member == null ? null : profile.member.name;
				if (playerName != null && !playerName.isBlank()) data.refreshProfile(playerName);
				message("Je collection log is gesynchroniseerd met Zweetnekken.");
			}
			else
			{
				message("Zweetnekken heeft je collection log in de wachtrij gezet en probeert het later opnieuw.");
			}
		});
	}

	private boolean isPohLog()
	{
		return client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) == 1;
	}

	private void message(String text)
	{
		client.addChatMessage(ChatMessageType.CONSOLE, "Zweetnekken", text, "Zweetnekken");
	}

	public String overlayMessage()
	{
		if (uploadInProgress) return CollectionLogSyncOverlay.SYNCING_MESSAGE;
		if (waitingForCapture) return CollectionLogSyncOverlay.READING_MESSAGE;
		return CollectionLogSyncOverlay.MESSAGE;
	}
}

