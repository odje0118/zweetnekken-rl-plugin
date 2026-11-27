package com.zweetnekken.pb;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DiaryChatMessages
{
	private static final Pattern LEGACY_FORMAT = Pattern.compile("@[A-Za-z][A-Za-z0-9_]*@");
	private static final Pattern COLOSSEUM_WAVE = Pattern.compile("^Wave [0-9]+ completed!.*", Pattern.CASE_INSENSITIVE);
	private static final Pattern DOOM_CLEAR = Pattern.compile("^Level\\s+(?<level>[1-9][0-9]*)\\s+Complete!$",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern DOOM_DELVE_DURATION = Pattern.compile(
		"^Delve level (?<level>[1-9][0-9]*)(?:\\+\\s*\\((?<deepLevel>[1-9][0-9]*)\\))? duration:\\s*"
			+ "[0-9]+(?::[0-9]{2}){1,2}(?:\\.[0-9]+)?(?=[.,\\s]|$)",
		Pattern.CASE_INSENSITIVE);

	private static final Pattern SPECIAL_ACTIVITY_KILL_COUNT = Pattern.compile(
		"^Your (?<boss>Vardorvis|TzTok-Jad|TzKal-Zuk|Sol Heredit|(?:The )?Corrupted Gauntlet|Gauntlet) "
			+ "(?:kill |completion )?count is: [0-9,]+\\.?$", Pattern.CASE_INSENSITIVE);
	private static final Pattern SPECIAL_ACTIVITY_COMPLETION = Pattern.compile(
		"^(?:Congratulations!?\\s*)?(?:You have )?(?:completed|defeated|killed) (?:the )?"
			+ "(?<boss>Fortis Colosseum|Colosseum|Inferno|TzKal-Zuk|TzHaar Fight Cave|Fight Caves|"
			+ "The Corrupted Gauntlet|Corrupted Gauntlet|Gauntlet|Sol Heredit|TzTok-Jad)\\.?$", Pattern.CASE_INSENSITIVE);
	private static final Pattern NEW_PB_DURATION = Pattern.compile(
		"^Duration:?\\s*(?<time>[0-9:]+(?:\\.[0-9]+)?)\\.?\\s*\\(new personal best\\)\\.?$",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern OBSERVED_ACTIVITY_DURATION = Pattern.compile(
		"(?:Fight |Challenge |Corrupted challenge |Colosseum )?duration:?\\s*(?<time>[0-9:]+(?:\\.[0-9]+)?)"
			+ "(?:\\.\\s*Personal best:\\s*[0-9:]+(?:\\.[0-9]+)?|\\s*\\(new personal best\\)|[.]?$)",
		Pattern.CASE_INSENSITIVE);

	private DiaryChatMessages() {}

	static String normalize(String message)
	{
		return LEGACY_FORMAT.matcher(message).replaceAll("").replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
	}

	static Long doomLevel(String message)
	{
		Matcher clear = DOOM_CLEAR.matcher(message);
		String level;
		if (clear.matches())
		{
			level = clear.group("level");
		}
		else
		{
			Matcher delve = DOOM_DELVE_DURATION.matcher(message);
			if (!delve.find()) return null;
			level = delve.group("deepLevel");
			if (level == null) level = delve.group("level");
		}
		try { return Long.valueOf(level); }
		catch (NumberFormatException e) { return null; }
	}

	static String soloCompletionActivity(String message)
	{
		Matcher boss = SPECIAL_ACTIVITY_KILL_COUNT.matcher(message);
		if (!boss.matches())
		{
			boss = SPECIAL_ACTIVITY_COMPLETION.matcher(message);
			if (!boss.matches()) return null;
		}
		String name = boss.group("boss").toLowerCase(Locale.ROOT);
		if (name.equals("tztok-jad") || name.contains("fight cave")) return "TzHaar Fight Cave";
		if (name.equals("tzkal-zuk") || name.equals("inferno")) return "Inferno";
		if (name.equals("sol heredit") || name.contains("colosseum")) return "Fortis Colosseum";
		if (name.contains("corrupted gauntlet")) return "Corrupted Gauntlet";
		if (name.equals("gauntlet")) return "Gauntlet";
		return "Vardorvis";
	}

	static String soloDuration(String message)
	{
		if (COLOSSEUM_WAVE.matcher(message).matches()) return null;
		Matcher duration = OBSERVED_ACTIVITY_DURATION.matcher(message);
		return duration.find() ? duration.group("time") : null;
	}

	static String personalBestDuration(String message)
	{
		Matcher duration = NEW_PB_DURATION.matcher(message);
		return duration.matches() ? duration.group("time") : null;
	}
}
