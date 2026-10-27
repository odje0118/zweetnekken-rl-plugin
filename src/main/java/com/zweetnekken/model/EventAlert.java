package com.zweetnekken.model;

import java.util.List;

/** The JSON shape published by the event-alert API, including recent announcements. */
public final class EventAlert
{
	private String message;
	private String time;
	private String serverTime;
	private List<EventAlert> history;

	public EventAlert() {}

	public EventAlert(String message, String time)
	{
		this.message = message;
		this.time = time;
	}

	public String getMessage() { return message; }
	public String getTime() { return time; }
	public String getServerTime() { return serverTime; }
	public List<EventAlert> getHistory() { return history; }
}
