package com.zweetnekken.evidence;

import com.google.gson.Gson;
import com.zweetnekken.model.ZweetnekkenModels;
import com.zweetnekken.service.ImageCache;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.util.Filepath;

@Singleton
public class EvidenceStore
{
	private final Filepath root;
	private final Gson gson;

	@Inject
	public EvidenceStore(Gson gson, Filepath pluginDirectory)
	{
		this.gson = gson;
		this.root = pluginDirectory;
	}

	public Record save(ZweetnekkenModels.EventEnvelope envelope, BufferedImage image) throws IOException
	{
		ScreenshotEncoder.Encoded encoded = ScreenshotEncoder.encode(image);
		LocalDate date = LocalDate.now();
		String player = ImageCache.safeName(envelope.player.name);
		Filepath dir = root.join("evidence", player, date.toString());
		dir.createDirectories();
		Filepath imagePath = dir.joinSegment(envelope.eventId + "." + encoded.format);
		imagePath.write(encoded.bytes);

		Record record = new Record();
		record.metadata = envelope;
		record.screenshotPath = "evidence/" + player + "/" + date + "/" + imagePath.getFileName();
		record.format = encoded.format;
		record.status = ZweetnekkenModels.EventStatus.PENDING;
		record.updatedAt = Instant.now().toString();
		writeRecord(record);
		return record;
	}

	public synchronized Record saveMetadata(ZweetnekkenModels.EventEnvelope envelope) throws IOException
	{
		Record record = new Record();
		record.metadata = envelope;
		record.status = ZweetnekkenModels.EventStatus.PENDING;
		record.updatedAt = Instant.now().toString();
		writeRecord(record);
		return record;
	}

	public synchronized void writeRecord(Record record) throws IOException
	{
		Filepath outbox = root.joinSegment("outbox");
		outbox.createDirectories();
		String name = record.metadata.eventId + ".json";
		Filepath target = outbox.joinSegment(name);
		Filepath temp = outbox.joinSegment(name + ".tmp");
		temp.write(gson.toJson(record));
		temp.moveTo(target, StandardCopyOption.REPLACE_EXISTING);
	}

	public synchronized List<Record> records()
	{
		Filepath outbox = root.joinSegment("outbox");
		if (!outbox.isDirectory()) return new ArrayList<>();
		try (Stream<Filepath> paths = outbox.walk(1))
		{
			return paths.filter(path -> path.isFile() && path.getFileName().endsWith(".json"))
				.map(this::read)
				.filter(java.util.Objects::nonNull)
				.sorted(Comparator.comparing((Record r) ->
					r.metadata == null || r.metadata.capturedAt == null ? "" : r.metadata.capturedAt).reversed())
				.collect(Collectors.toList());
		}
		catch (IOException e)
		{
			return new ArrayList<>();
		}
	}

	private Record read(Filepath path)
	{
		try (Reader reader = path.openReader())
		{
			Record record = gson.fromJson(reader, Record.class);
			if (record != null && record.screenshotPath != null)
				record.screenshotPath = relativeScreenshotPath(record.screenshotPath);
			return record;
		}
		catch (IOException | RuntimeException e)
		{
			return null;
		}
	}

	/**
	 * Resolve a persisted screenshot path inside this plugin's data directory.
	 * Old records stored absolute paths under .runelite/zweetnekken; normalize
	 * those paths after RuneLite migrates the legacy directory to plugin-data.
	 */
	public Filepath resolveScreenshot(String storedPath)
	{
		if (storedPath == null || storedPath.isBlank()) return null;
		String relative = relativeScreenshotPath(storedPath);
		if (isAbsolutePath(relative)) return null;
		try
		{
			Filepath path = root.join(relative);
			return path.isFile() ? path : null;
		}
		catch (IllegalArgumentException e)
		{
			return null;
		}
	}

	private static String relativeScreenshotPath(String storedPath)
	{
		if (storedPath == null) return null;
		String normalized = storedPath.replace('\\', '/');
		if (!isAbsolutePath(normalized)) return normalized;

		String marker = "/plugin-data/zweetnekken/";
		int index = normalized.lastIndexOf(marker);
		if (index >= 0) return normalized.substring(index + marker.length());

		marker = "/zweetnekken/";
		index = normalized.lastIndexOf(marker);
		if (index >= 0) return normalized.substring(index + marker.length());
		return normalized;
	}

	private static boolean isAbsolutePath(String value)
	{
		return value != null && (value.startsWith("/") || value.matches("^[A-Za-z]:/.*"));
	}

	public synchronized void deleteLocal(String eventId)
	{
		Filepath recordFile;
		try
		{
			recordFile = root.join("outbox", eventId + ".json");
		}
		catch (IllegalArgumentException e)
		{
			return;
		}
		Record record = read(recordFile);
		try
		{
			if (record != null && record.screenshotPath != null)
			{
				Filepath screenshot = resolveScreenshot(record.screenshotPath);
				if (screenshot != null) screenshot.deleteIfExists();
			}
			recordFile.deleteIfExists();
		}
		catch (IOException ignored) { }
	}

	public synchronized void pruneUploaded(int retentionDays)
	{
		long cutoff = System.currentTimeMillis() - Math.max(0, retentionDays) * 86_400_000L;
		for (Record record : records())
		{
			if ((record.status == ZweetnekkenModels.EventStatus.DRAFT || record.status == ZweetnekkenModels.EventStatus.SUBMITTED || record.status == ZweetnekkenModels.EventStatus.APPROVED)
				&& parseTime(record.updatedAt) < cutoff) deleteLocal(record.metadata.eventId);
		}
	}

	private static long parseTime(String value) { try { return Instant.parse(value).toEpochMilli(); } catch (RuntimeException e) { return Long.MAX_VALUE; } }

	public static final class Record
	{
		public ZweetnekkenModels.EventEnvelope metadata;
		public String screenshotPath;
		public String format;
		public ZweetnekkenModels.EventStatus status;
		public String submissionId;
		public String error;
		public int retryCount;
		public String updatedAt;
	}
}
