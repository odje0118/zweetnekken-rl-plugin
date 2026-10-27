/*
 * Event-alert polling and baseline behavior adapted from Odablock Sounds.
 * See LICENSES/odablock-sounds-LICENSE.txt.
 */
package com.zweetnekken.service;

import com.zweetnekken.ZweetnekkenConfig;
import com.zweetnekken.model.EventAlert;
import com.zweetnekken.api.ZweetnekkenApiClient;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;

@Slf4j
@Singleton
public class EventAlertService
{
	static final int POLL_INTERVAL_TICKS = 10;
	static final long OVERLAY_DURATION_MILLIS = 15_000L;
	private static final int MAX_SEEN_ALERT_KEYS = 300;

	private final Client client;
	private final ZweetnekkenApiClient api;
	private final ChatMessageManager chat;
	private final ZweetnekkenConfig config;
	private final ScheduledExecutorService executor;
	private final AtomicBoolean requestInFlight = new AtomicBoolean();
	private final Object stateLock = new Object();
	private final Set<String> seenAlertKeys = new LinkedHashSet<>();
	private Instant observationStartedAt = Instant.now();
	private volatile long serverClockOffsetMillis;
	private volatile boolean serverClockOffsetKnown;
	private int lastCheckedTick = -1;
	private int generation;
	private volatile String overlayMessage;
	private volatile long overlayExpiresAt;

	@Inject
	public EventAlertService(Client client, ZweetnekkenApiClient api,
		ChatMessageManager chat, ZweetnekkenConfig config, ScheduledExecutorService executor)
	{
		this.client = client;
		this.api = api;
		this.chat = chat;
		this.config = config;
		this.executor = executor;
	}

	public void onGameTick()
	{
		if (!config.eventAlerts()) return;
		int currentTick = client.getTickCount();
		if (lastCheckedTick != -1 && currentTick >= lastCheckedTick
			&& currentTick - lastCheckedTick <= POLL_INTERVAL_TICKS) return;
		if (!requestInFlight.compareAndSet(false, true)) return;
		lastCheckedTick = currentTick;
		final int requestGeneration;
		synchronized (stateLock) { requestGeneration = generation; }
		try
		{
			executor.execute(() -> requestAlert(requestGeneration));
		}
		catch (RuntimeException e)
		{
			requestInFlight.set(false);
			log.error("Unable to schedule Zweetnekken event-alert request", e);
		}
	}

	public void resetStateForWorldHopOrLogin()
	{
		resetState(false);
	}

	public void resetStateForLoginScreen()
	{
		resetState(true);
	}

	public void resetStateForConfigChange()
	{
		resetState(true);
	}

	private void resetState(boolean resetObservationWindow)
	{
		lastCheckedTick = -1;
		synchronized (stateLock)
		{
			generation++;
			if (resetObservationWindow)
			{
				observationStartedAt = Instant.now();
			}
		}
		clearOverlay();
	}

	public String activeOverlayMessage()
	{
		if (System.currentTimeMillis() >= overlayExpiresAt)
		{
			clearOverlay();
			return null;
		}
		return overlayMessage;
	}

	private void requestAlert(int requestGeneration)
	{
		api.getEventAlert(result ->
		{
			try
			{
				if (!result.isSuccessful())
				{
					log.warn("Zweetnekken event-alert API request failed: HTTP {}, {}",
						result.statusCode, result.error);
					return;
				}
				updateServerClockOffset(result.value == null ? null : result.value.getServerTime());
				acceptAlert(result.value, requestGeneration);
			}
			finally
			{
				requestInFlight.set(false);
			}
		});
	}

	void acceptAlert(EventAlert alert)
	{
		final int currentGeneration;
		synchronized (stateLock) { currentGeneration = generation; }
		acceptAlert(alert, currentGeneration);
	}

	private void acceptAlert(EventAlert alert, int requestGeneration)
	{
		List<EventAlert> alerts = alert == null ? null : alert.getHistory();
		log.debug("Received RuneLite event-alert response: currentMessagePresent={}, historyCount={}, serverTimePresent={}",
			alert != null && alert.getMessage() != null && !alert.getMessage().trim().isEmpty(),
			alerts == null ? 0 : alerts.size(),
			alert != null && alert.getServerTime() != null && !alert.getServerTime().trim().isEmpty());
		if (alerts == null || alerts.isEmpty())
		{
			// Backward compatibility with an API payload that only contains message/time.
			if (alert == null || alert.getMessage() == null || alert.getMessage().trim().isEmpty())
			{
				return;
			}
			alerts = Collections.singletonList(alert);
		}

		for (EventAlert published : alerts)
		{
			if (published == null || published.getMessage() == null || published.getMessage().trim().isEmpty())
			{
				continue;
			}
			String message = published.getMessage().trim();
			String key = alertKey(published, message);
			boolean shouldSend;
			synchronized (stateLock)
			{
				if (requestGeneration != generation)
				{
					return;
				}
				if (!seenAlertKeys.add(key))
				{
					continue;
				}
				while (seenAlertKeys.size() > MAX_SEEN_ALERT_KEYS)
				{
					Iterator<String> iterator = seenAlertKeys.iterator();
					iterator.next();
					iterator.remove();
				}
				shouldSend = wasPublishedAfterStartup(published.getTime());
			}
			if (!shouldSend)
			{
				continue;
			}

			log.info("Delivering Zweetnekken event alert: {}", message);
			sendChatMessage(message);
			overlayMessage = message;
			overlayExpiresAt = System.currentTimeMillis() + Math.max(1, config.eventAlertDuration()) * 1000L;
		}
	}

	private String alertKey(EventAlert alert, String message)
	{
		String time = alert.getTime();
		return (time == null || time.trim().isEmpty() ? "message" : time.trim()) + "|" + message;
	}

	private void updateServerClockOffset(String serverTime)
	{
		if (serverTime == null || serverTime.trim().isEmpty()) return;
		try
		{
			long offset = BackendClockTimestampParser.parse(serverTime).toEpochMilli() - System.currentTimeMillis();
			serverClockOffsetMillis = offset;
			serverClockOffsetKnown = true;
		}
		catch (DateTimeParseException e)
		{
			log.warn("Ignoring invalid Zweetnekken backend clock timestamp: {}", serverTime);
		}
	}

	private boolean wasPublishedAfterStartup(String time)
	{
		if (time == null || time.trim().isEmpty())
		{
			log.warn("RuneLite event alert has no timestamp; displaying it rather than hiding it.");
			return true;
		}
		try
		{
			Instant publishedAt = OffsetDateTime.parse(time.trim()).toInstant();
			Instant cutoff = observationStartedAt.minusSeconds(2);
			if (serverClockOffsetKnown)
			{
				cutoff = cutoff.plusMillis(serverClockOffsetMillis);
			}
			return !publishedAt.isBefore(cutoff);
		}
		catch (DateTimeParseException e)
		{
			log.warn("RuneLite event alert timestamp could not be parsed; displaying alert to avoid dropping it: {}", time);
			return true;
		}
	}

	private void sendChatMessage(String alert)
	{
		String formatted = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT)
			.append("Zweetnekken Event Alert ~ ")
			.append(alert)
			.build();
		String color = String.format("%06x", config.eventAlertColor().getRGB() & 0xffffff);
		chat.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(formatted.replace("colHIGHLIGHT", "col=" + color))
			.build());
	}

	private void clearOverlay()
	{
		overlayMessage = null;
		overlayExpiresAt = 0L;
	}
}
