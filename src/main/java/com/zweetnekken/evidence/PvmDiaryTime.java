package com.zweetnekken.evidence;

import com.zweetnekken.model.ZweetnekkenModels;
import java.util.Map;

final class PvmDiaryTime
{
	private PvmDiaryTime() {}

	static boolean improvesPublishedDiaryTime(String activityId, long millis, ZweetnekkenModels.PvmDiaryStatus status)
	{
		if (status != null && status.activities != null)
		{
			for (ZweetnekkenModels.PvmDiaryActivity activity : status.activities)
				if (activity != null && activityId.equals(activity.id) && activity.bestResult != null
					&& activity.bestResult.value != null && activity.bestResult.value > 0
					&& millis >= activity.bestResult.value) return false;
		}
		return true;
	}

	static boolean hasFasterOrEqualDiaryTime(ZweetnekkenModels.EventEnvelope event,
		ZweetnekkenModels.EventEnvelope previous, Object activityId, long millis)
	{
		if (previous == null || previous.player == null || !"diary".equals(previous.eventType)
			|| !event.player.accountHash.equals(previous.player.accountHash)) return false;
		Map<?, ?> diary = diaryDetails(previous);
		Object value = diary.get("value");
		return activityId.equals(diary.get("activityId")) && value instanceof Number
			&& ((Number) value).longValue() > 0 && ((Number) value).longValue() <= millis;
	}

	static Map<?, ?> diaryDetails(ZweetnekkenModels.EventEnvelope event)
	{
		Object diary = event.details == null ? null : event.details.get("pvmDiary");
		return diary instanceof Map ? (Map<?, ?>) diary : Map.of();
	}
}
