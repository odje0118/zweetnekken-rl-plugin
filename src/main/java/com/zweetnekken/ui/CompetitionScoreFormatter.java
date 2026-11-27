package com.zweetnekken.ui;

import java.text.NumberFormat;
import java.util.Locale;

/** Compact score labels for competition leaderboards. */
final class CompetitionScoreFormatter
{
	private CompetitionScoreFormatter() { }

	static String format(long score, String unit, String displayValue)
	{
		if ("xp".equalsIgnoreCase(unit == null ? "" : unit.trim()))
		{
			if (score >= 1_000_000L) return compactXp(score / 1_000_000.0, "M");
			if (score >= 1_000L) return compactXp(score / 1_000.0, "K");
			return number(score) + " XP";
		}
		if (displayValue != null && !displayValue.isBlank()) return displayValue;
		return number(score) + " " + (unit == null || unit.isBlank() ? "—" : unit);
	}

	private static String compactXp(double amount, String suffix)
	{
		String formatted = String.format(Locale.US, "%.1f", amount);
		if (formatted.endsWith(".0")) formatted = formatted.substring(0, formatted.length() - 2);
		return formatted + suffix + " XP";
	}

	private static String number(long value)
	{
		return NumberFormat.getIntegerInstance(Locale.US).format(value);
	}
}
