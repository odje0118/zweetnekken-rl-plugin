package com.zweetnekken.ui;

import com.zweetnekken.model.ZweetnekkenModels;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The initial PvM Diary catalogue. The server remains authoritative for awards. */
final class PvmDiaryCatalogue
{
	private PvmDiaryCatalogue() {}

	static ZweetnekkenModels.PvmDiaryStatus create()
	{
		ZweetnekkenModels.PvmDiaryStatus status = new ZweetnekkenModels.PvmDiaryStatus();
		status.catalogueVersion = "2026-01";
		category(status, "COX",
			activity("cox-solo", "CoX Solo", 1, time("Easy", "20:00", 0), time("Medium", "17:00", 50), time("Hard", "14:30", 100)),
			activity("cox-trios", "CoX Trios", 3, time("Easy", "17:00", 0), time("Medium", "14:40", 50), time("Hard", "12:00", 100)),
			activity("cox-5s", "CoX 5s", 5, time("Easy", "15:00", 0), time("Medium", "12:30", 50), time("Hard", "11:15", 100)));
		category(status, "COX CM",
			activity("cox-cm-solo", "COX CM Solo", 1, time("Easy", "40:00", 0), time("Medium", "38:30", 75), time("Hard", "27:00", 125)),
			activity("cox-cm-trios", "COX CM Trios", 3, time("Easy", "30:00", 0), time("Medium", "27:00", 50), time("Hard", "20:00", 100)),
			activity("cox-cm-5s", "COX CM 5s", 5, time("Easy", "30:00", 0), time("Medium", "25:00", 50), time("Hard", "18:00", 100)));
		category(status, "TOB",
			activity("tob-solo", "TOB Solo", 1, completion("Hard", 90)),
			activity("tob-duo", "TOB Duo", 2, time("Easy", "28:00", 0), time("Medium", "26:00", 30), time("Hard", "23:30", 60)),
			activity("tob-trio", "TOB Trio", 3, time("Easy", "19:00", 0), time("Medium", "17:30", 30), time("Hard", "16:30", 60)),
			activity("tob-4s", "TOB 4s", 4, time("Easy", "17:30", 0), time("Medium", "15:00", 30), time("Hard", "14:15", 60)),
			activity("tob-5s", "TOB 5s", 5, time("Easy", "15:00", 0), time("Medium", "14:15", 30), time("Hard", "13:30", 60)));
		category(status, "TOB HMT",
			activity("hmt-duo", "HMT Duo", 2, completion("Medium", 50)),
			activity("hmt-trios", "HMT Trios", 3, time("Easy", "26:00", 0), time("Medium", "23:00", 50), time("Hard", "20:00", 100)),
			activity("hmt-4s", "HMT 4s", 4, time("Easy", "24:00", 0), time("Medium", "21:00", 50), time("Hard", "17:00", 100)),
			activity("hmt-5s", "HMT 5s", 5, time("Easy", "22:00", 0), time("Medium", "19:00", 50), time("Hard", "15:00", 100)));
		category(status, "TOA 300", activity("toa-300-solo", "TOA 300 Solo", 1,
			time("Easy", "23:00", 0), time("Medium", "21:00", 25), time("Hard", "19:00", 75)));
		category(status, "TOA 500", activity("toa-500-solo", "TOA 500 Solo", 1,
			time("Easy", "35:00", 0), time("Medium", "30:00", 25), time("Hard", "26:00", 75)));
		category(status, "Inferno", activity("inferno-solo", "Inferno", 1,
			time("Easy", "80:00", 0), time("Medium", "65:00", 100), time("Hard", "50:00", 200)));
		category(status, "Fight Caves", activity("fight-caves-solo", "Fight Caves", 1,
			time("Easy", "29:00", 0), time("Medium", "26:30", 35), time("Hard", "23:00", 65)));
		category(status, "Colosseum", activity("colosseum-solo", "Colosseum", 1,
			time("Easy", "30:00", 0), time("Medium", "24:00", 75), time("Hard", "18:00", 125)));
		category(status, "Doom", activity("doom", "Doom", 1,
			wave("Easy", 10, 0), wave("Medium", 16, 35), wave("Hard", 40, 65)));
		category(status, "CG", activity("cg-solo", "Corrupted Gauntlet", 1,
			time("Easy", "7:00", 0), time("Medium", "6:05", 75), time("Hard", "5:20", 125)));

		for (ZweetnekkenModels.PvmDiaryCategory c : status.categories)
		{
			for (ZweetnekkenModels.PvmDiaryActivity a : status.activities)
			{
				if (!c.name.equals(a.category)) continue;
				c.totalTiers += a.tiers.size();
				for (ZweetnekkenModels.PvmDiaryTier t : a.tiers) c.totalPoints += t.points;
			}
			status.totalTiers += c.totalTiers;
			status.totalPoints += c.totalPoints;
		}
		return status;
	}

	private static void category(ZweetnekkenModels.PvmDiaryStatus status, String name, ZweetnekkenModels.PvmDiaryActivity... activities)
	{
		ZweetnekkenModels.PvmDiaryCategory c = new ZweetnekkenModels.PvmDiaryCategory();
		c.name = name;
		status.categories.add(c);
		for (ZweetnekkenModels.PvmDiaryActivity a : activities)
		{
			a.category = name;
			status.activities.add(a);
		}
	}

	private static ZweetnekkenModels.PvmDiaryActivity activity(String id, String name, int teamSize, ZweetnekkenModels.PvmDiaryTier... tiers)
	{
		ZweetnekkenModels.PvmDiaryActivity a = new ZweetnekkenModels.PvmDiaryActivity();
		a.id = id; a.name = name; a.teamSize = teamSize;
		a.kind = "doom".equals(id) ? "wave" : (tiers[0].target == null ? "completion" : "time");
		a.tiers.addAll(Arrays.asList(tiers));
		for (ZweetnekkenModels.PvmDiaryTier t : tiers) a.earnedPoints += t.completed ? t.points : 0;
		return a;
	}

	private static ZweetnekkenModels.PvmDiaryTier time(String name, String time, long points)
	{
		ZweetnekkenModels.PvmDiaryTier t = new ZweetnekkenModels.PvmDiaryTier();
		t.tier = name; t.target = millis(time); t.targetLabel = time; t.points = points;
		return t;
	}

	private static ZweetnekkenModels.PvmDiaryTier completion(String name, long points)
	{
		ZweetnekkenModels.PvmDiaryTier t = new ZweetnekkenModels.PvmDiaryTier();
		t.tier = name; t.targetLabel = "Completion"; t.points = points;
		return t;
	}

	private static ZweetnekkenModels.PvmDiaryTier wave(String name, int level, long points)
	{
		ZweetnekkenModels.PvmDiaryTier t = new ZweetnekkenModels.PvmDiaryTier();
		t.tier = name; t.target = (long) level; t.targetLabel = "Wave " + level; t.points = points;
		return t;
	}

	private static long millis(String value)
	{
		String[] parts = value.split(":");
		long seconds = Long.parseLong(parts[parts.length - 1]);
		if (parts.length > 1) seconds += 60L * Long.parseLong(parts[parts.length - 2]);
		if (parts.length > 2) seconds += 3600L * Long.parseLong(parts[parts.length - 3]);
		return seconds * 1000L;
	}
}

