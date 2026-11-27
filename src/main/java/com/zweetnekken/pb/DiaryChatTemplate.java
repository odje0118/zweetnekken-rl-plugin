package com.zweetnekken.pb;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DiaryChatTemplate
{
	private static final Pattern TOKEN = Pattern.compile("\\{(wave|time|rest)}");
	private static final String TIME = "[0-9]{1,4}:[0-5][0-9](?::[0-5][0-9])?(?:\\.[0-9]{1,3})?";
	private final Pattern pattern;

	private DiaryChatTemplate(Pattern pattern)
	{
		this.pattern = pattern;
	}

	static DiaryChatTemplate compile(String template, boolean wave)
	{
		if (template == null || template.length() > 240) return null;
		Matcher token = TOKEN.matcher(template);
		StringBuilder pattern = new StringBuilder();
		boolean hasResult = false;
		boolean hasTime = false;
		int end = 0;
		while (token.find())
		{
			String literal = template.substring(end, token.start());
			if ((end == 0 && literal.strip().length() < 5) || literal.contains("{") || literal.contains("}")) return null;
			pattern.append(Pattern.quote(literal));
			switch (token.group(1))
			{
				case "wave":
					if (!wave || hasResult) return null;
					pattern.append("(?<result>[1-9][0-9]{0,8})");
					hasResult = true;
					break;
				case "time":
					if (hasTime) return null;
					pattern.append(wave ? TIME : "(?<result>" + TIME + ")");
					hasTime = true;
					hasResult |= !wave;
					break;
				default:
					if (token.end() != template.length()) return null;
					pattern.append("(?:[\\s.,;:(].{0,119})?");
			}
			end = token.end();
		}
		String tail = template.substring(end);
		if (!hasResult || tail.contains("{") || tail.contains("}")) return null;
		return new DiaryChatTemplate(Pattern.compile(pattern.append(Pattern.quote(tail)).toString(),
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
	}

	String result(String message)
	{
		if (message.length() > 512) return null;
		Matcher match = pattern.matcher(message);
		return match.matches() ? match.group("result") : null;
	}
}
