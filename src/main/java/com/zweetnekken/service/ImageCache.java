/*
 * Portions adapted from the Terpinheimer remote-image loading code.
 * See LICENSES/terpinheimer-LICENSE.txt.
 */
package com.zweetnekken.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import net.runelite.client.util.Filepath;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;
import java.util.Iterator;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.ImageIcon;
import javax.inject.Inject;
import javax.inject.Singleton;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class ImageCache
{
	private static final long PROFILE_TTL = Duration.ofHours(24).toMillis();
	private final Filepath root;
	private final OkHttpClient http;
	private final ScheduledExecutorService executor;

	@Inject
	public ImageCache(OkHttpClient http, ScheduledExecutorService executor, Filepath pluginDirectory)
	{
		this.http = http.newBuilder().followRedirects(false).followSslRedirects(false).build();
		this.executor = executor;
		this.root = pluginDirectory.joinSegment("cache");
	}

	public void loadProfile(String rsn, String url, Consumer<BufferedImage> callback)
	{
		String urlKey = url == null ? "none" : Integer.toHexString(url.hashCode());
		load(root.join("profiles", safeName(rsn) + "-" + urlKey + ".img"), url, PROFILE_TTL, false, callback);
	}

	public void loadWebsiteImage(String key, String url, Consumer<BufferedImage> callback)
	{
		load(root.join("website-images", safeName(key) + ".img"), url, 0, true, callback);
	}

	public void loadWebsiteImage(String key, String url, String fallbackUrl, Consumer<BufferedImage> callback)
	{
		Filepath target = root.join("website-images", safeName(key) + ".img");
		load(target, url, 0, true, image ->
		{
			if (image != null) callback.accept(image);
			else load(target, fallbackUrl, 0, true, callback);
		});
	}

	private void load(Filepath target, String url, long ttl, boolean revalidate, Consumer<BufferedImage> callback)
	{
		executor.execute(() -> loadInBackground(target, url, ttl, revalidate, callback));
	}

	private void loadInBackground(Filepath target, String url, long ttl, boolean revalidate, Consumer<BufferedImage> callback)
	{
		BufferedImage cached = read(target);
		if (cached != null && !revalidate) callback.accept(cached);
		boolean fresh = false;
		try { fresh = ttl > 0 && target.exists() && System.currentTimeMillis() - target.getLastModifiedTime().toMillis() < ttl; }
		catch (IOException ignored) { }
		if (fresh || url == null || url.isBlank()) { if (cached == null || revalidate) callback.accept(null); return; }
		Request request;
		try
		{
			Request.Builder builder = new Request.Builder().url(url).get();
			if (revalidate) builder.header("Cache-Control", "no-cache, no-store").header("Pragma", "no-cache");
			request = builder.build();
		}
		catch (IllegalArgumentException e) { if (cached == null || revalidate) callback.accept(null); return; }
		http.newCall(request).enqueue(new Callback()
		{
			@Override public void onFailure(Call call, IOException e) { if (cached == null || revalidate) callback.accept(null); }
			@Override public void onResponse(Call call, Response response)
			{
				try (Response ignored = response)
				{
					if (!response.isSuccessful() || response.body() == null) { if (cached == null || revalidate) callback.accept(null); return; }
					target.getParent().createDirectories();
					Filepath temp = target.getParent().joinSegment(target.getFileName() + ".tmp");
					temp.write(response.body().bytes());
					BufferedImage downloaded = read(temp);
					if (downloaded == null) { temp.deleteIfExists(); if (cached == null || revalidate) callback.accept(null); return; }
					temp.moveTo(target, StandardCopyOption.REPLACE_EXISTING);
					callback.accept(downloaded);
				}
				catch (IOException e) { if (cached == null || revalidate) callback.accept(null); }
			}
		});
	}

	/**
	 * Loads raw image bytes into an ImageIcon so animated GIF frames remain animated.
	 * Network requests and file reads are handled off the Swing event thread.
	 */
	public void loadWebsiteIcon(String key, String url, Consumer<ImageIcon> callback)
	{
		Filepath target = root.join("website-images", safeName(key) + ".bin");
		executor.execute(() -> loadWebsiteIconInBackground(target, url, callback));
	}

	private void loadWebsiteIconInBackground(Filepath target, String url, Consumer<ImageIcon> callback)
	{
		// Only display artwork downloaded from the current backend. Never render a stale banner cache.
		if (url == null || url.isBlank()) { callback.accept(null); return; }

		Request request;
		try
		{
			request = new Request.Builder().url(url).get()
				.header("Cache-Control", "no-cache, no-store")
				.header("Pragma", "no-cache")
				.build();
		}
		catch (IllegalArgumentException e)
		{
			log.warn("Invalid animated banner URL for '{}'.", target.getFileName(), e);
			callback.accept(null);
			return;
		}

		http.newCall(request).enqueue(new Callback()
		{
			@Override public void onFailure(Call call, IOException e)
			{
				log.warn("Failed to download animated banner '{}'.", target.getFileName(), e);
				callback.accept(null);
			}

			@Override public void onResponse(Call call, Response response)
			{
				try (Response ignored = response)
				{
					if (!response.isSuccessful() || response.body() == null)
					{
						log.warn("Animated banner request returned HTTP {} (redirect: {}).",
							response.code(), response.header("Location"));
						callback.accept(null);
						return;
					}
					String contentType = response.header("Content-Type");
					byte[] bytes = response.body().bytes();
					AnimatedGifDisposalNormalizer.normalizeOpaqueFullFrameGifDisposal(bytes);
					int frames = gifFrameCount(bytes);
					ImageIcon downloaded = frames > 1 ? createIcon(bytes) : null;
					if (downloaded == null)
					{
						log.warn("Animated banner response is not an animated GIF (frames: {}, content-type: {}, bytes: {}).",
							frames, contentType, bytes.length);
						callback.accept(null);
						return;
					}
					log.debug("Downloaded animated banner ({} frames, {} bytes, {}x{}, content-type: {}).",
						frames, bytes.length, downloaded.getIconWidth(), downloaded.getIconHeight(), contentType);
					target.getParent().createDirectories();
					Filepath temp = target.getParent().joinSegment(target.getFileName() + ".tmp");
					temp.write(bytes);
					temp.moveTo(target, StandardCopyOption.REPLACE_EXISTING);
					callback.accept(downloaded);
				}
				catch (IOException e)
				{
					log.warn("Could not cache animated banner '{}'.", target.getFileName(), e);
					callback.accept(null);
				}
			}
		});
	}

	private static ImageIcon createIcon(byte[] bytes)
	{
		// Do not let an old/stale JPG cache be mistaken for an animated banner.
		if (!isGif(bytes)) return null;
		ImageIcon icon = new ImageIcon(bytes);
		return icon.getIconWidth() > 0 && icon.getIconHeight() > 0 ? icon : null;
	}

	
	private static boolean isGif(byte[] bytes)
	{
		if (bytes == null || bytes.length < 6) return false;
		boolean gif87a = bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F'
			&& bytes[3] == '8' && bytes[4] == '7' && bytes[5] == 'a';
		boolean gif89a = bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F'
			&& bytes[3] == '8' && bytes[4] == '9' && bytes[5] == 'a';
		return gif87a || gif89a;
	}

	private static int gifFrameCount(byte[] bytes)
	{
		if (!isGif(bytes)) return 0;
		try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes)))
		{
			if (input == null) return 0;
			Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) return 0;
			ImageReader reader = readers.next();
			try
			{
				reader.setInput(input, false, false);
				return reader.getNumImages(true);
			}
			finally
			{
				reader.dispose();
			}
		}
		catch (IOException e)
		{
			log.warn("Could not inspect animated GIF frame count.", e);
			return 0;
		}
	}

	public void clear()
	{
		clear(null);
	}

	public void clear(Runnable afterClear)
	{
		executor.execute(() ->
		{
			deleteChildren(root);
			if (afterClear != null) afterClear.run();
		});
	}

	private static void deleteChildren(Filepath path)
	{
		if (!path.exists()) return;
		try (java.util.stream.Stream<Filepath> stream = path.walk())
		{
			stream.sorted(java.util.Comparator.reverseOrder())
				.filter(p -> !p.equals(path))
				.forEach(p -> { try { p.deleteIfExists(); } catch (IOException ignored) { } });
		}
		catch (IOException ignored) { }
	}

	private static BufferedImage read(Filepath path)
	{
		if (!path.isFile()) return null;
		try (var input = path.openInputStream()) { return ImageIO.read(input); }
		catch (IOException e) { return null; }
	}

	public static String safeName(String value)
	{
		String normalized = value == null ? "unknown" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "_");
		return normalized.isEmpty() ? "unknown" : normalized;
	}
}
