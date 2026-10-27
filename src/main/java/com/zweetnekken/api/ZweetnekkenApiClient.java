/*
 * Portions adapted from the Llama Club and PB Tracker Sync HTTP clients.
 * See LICENSES/llama-LICENSE.txt and LICENSES/pb-tracker-sync-LICENSE.txt.
 */
package com.zweetnekken.api;

import com.google.gson.Gson;
import com.zweetnekken.ZweetnekkenConfig;
import com.zweetnekken.model.ZweetnekkenModels;
import com.zweetnekken.model.EventAlert;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ZweetnekkenApiClient
{
	public static final String ZWEETNEKKEN_API_ORIGIN = "https://api-production-81001.up.railway.app";
	private static final Logger log = LoggerFactory.getLogger(ZweetnekkenApiClient.class);
	public static final String PLUGIN_VERSION = "1.1.41";
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private final OkHttpClient http;
	private final Gson gson;
	private final ZweetnekkenConfig config;
	private final String origin;
	private final ScheduledExecutorService executor;

	@Inject
	public ZweetnekkenApiClient(OkHttpClient http, Gson gson, ZweetnekkenConfig config, ScheduledExecutorService executor)
	{
		this(http, gson, config, ZWEETNEKKEN_API_ORIGIN, executor);
	}

	// Visible to package tests only; production requests use the Zweetnekken backend.
	ZweetnekkenApiClient(OkHttpClient http, Gson gson, ZweetnekkenConfig config, String origin)
	{
		this(http, gson, config, origin, null);
	}

	ZweetnekkenApiClient(OkHttpClient http, Gson gson, ZweetnekkenConfig config, String origin,
		ScheduledExecutorService executor)
	{
		this.http = http.newBuilder().followRedirects(false).followSslRedirects(false).build();
		this.gson = gson;
		this.config = config;
		this.origin = origin.replaceAll("/+$", "");
		this.executor = executor;
	}

	public interface ResultCallback<T> { void complete(ApiResult<T> result); }

	public static final class ApiResult<T>
	{
		public final int statusCode;
		public final T value;
		public final String error;
		private ApiResult(int statusCode, T value, String error) { this.statusCode = statusCode; this.value = value; this.error = error; }
		public static <T> ApiResult<T> ok(int code, T value) { return new ApiResult<>(code, value, null); }
		public static <T> ApiResult<T> error(int code, String error) { return new ApiResult<>(code, null, error); }
		public boolean isSuccessful() { return statusCode >= 200 && statusCode < 300; }
		public boolean isRetryable() { return statusCode == 0 || statusCode == 429 || statusCode >= 500; }
	}

	public void getProfile(String name, ResultCallback<ZweetnekkenModels.Profile> callback)
	{
		get("/api/clan/profile?name=" + encode(name), false, ZweetnekkenModels.Profile.class, callback);
	}

	public void getCompetitions(ResultCallback<ZweetnekkenModels.Competitions> callback)
	{
		get("/api/competitions", false, ZweetnekkenModels.Competitions.class, callback);
	}

	public void getCompetitionPanels(ResultCallback<ZweetnekkenModels.CompetitionPanels> callback)
	{
		get("/api/competitions/panels", false, ZweetnekkenModels.CompetitionPanels.class, callback);
	}

	public void validateSession(ResultCallback<Map> callback) { get("/api/runelite/session", true, Map.class, callback); }
	public void getRules(ResultCallback<ZweetnekkenModels.Rules> callback) { get("/api/runelite/rules", true, ZweetnekkenModels.Rules.class, callback); }
	public void getBingo(ResultCallback<ZweetnekkenModels.BingoEvent> callback)
	{
		get("/api/runelite/bingo", true, ZweetnekkenModels.BingoEvent.class, callback);
	}
	public void getBingoRules(ResultCallback<ZweetnekkenModels.BingoRules> callback)
	{
		get("/api/runelite/bingo/rules", true, ZweetnekkenModels.BingoRules.class, callback);
	}

	public void getEventAlert(ResultCallback<EventAlert> callback)
	{
		get("/api/runelite/event-alerts", false, EventAlert.class, callback);
	}

	public void uploadEvent(ZweetnekkenModels.EventEnvelope metadata, Path screenshot, String format,
		ResultCallback<ZweetnekkenModels.EventResponse> callback)
	{
		try
		{
			String metadataJson = gson.toJson(metadata);
			MultipartBody.Builder bodyBuilder = new MultipartBody.Builder().setType(MultipartBody.FORM)
				.addFormDataPart("metadata", null, RequestBody.create(JSON, metadataJson));
			long screenshotBytes = 0;
			if (screenshot != null)
			{
				screenshotBytes = Files.size(screenshot);
				MediaType imageType = MediaType.parse("image/" + ("jpg".equals(format) ? "jpeg" : format));
				bodyBuilder.addFormDataPart("screenshot", screenshot.getFileName().toString(), RequestBody.create(imageType, screenshot.toFile()));
			}
			MultipartBody body = bodyBuilder.build();
			execute(newRequest("/api/runelite/events", true).post(body).build(),
				ZweetnekkenModels.EventResponse.class, callback);
		}
		catch (IOException e)
		{
			log.error("Could not read evidence screenshot {}", screenshot, e);
			callback.complete(ApiResult.error(0, "Could not read evidence screenshot"));
		}
	}

	public void syncPbs(ZweetnekkenModels.PbBulkRequest request, boolean bulk, ResultCallback<Map> callback)
	{
		postJson(bulk ? "/api/runelite/personal-bests/bulk" : "/api/runelite/personal-bests", request, Map.class, callback);
	}

	public void syncPlayerProgress(ZweetnekkenModels.PlayerProgressRequest request, ResultCallback<Map> callback)
	{
		Runnable send = () -> postJson("/api/runelite/player-progress", request, Map.class, callback);
		if (executor == null) send.run();
		else executor.execute(send);
	}

	public void syncCollectionLog(ZweetnekkenModels.CollectionLogRequest request, ResultCallback<Map> callback)
	{
		postJson("/api/runelite/collection-log", request, Map.class, callback);
	}

	public void updateSubmission(String id, int partySize, int clanMembers, int nonClanMembers,
		List<ZweetnekkenModels.PartyMember> partyMembers, String notes, ResultCallback<Map> callback)
	{
		postJsonWithMethod("/api/runelite/submissions/" + encode(id), Map.of("submittedPartySize", partySize,
			"submittedClanMemberCount", clanMembers, "submittedNonClanMemberCount", nonClanMembers,
			"submittedPartyMembers", partyMembers == null ? List.of() : partyMembers, "notes", notes),
			"PATCH", Map.class, callback);
	}

	public void submit(String id, ResultCallback<Map> callback)
	{
		postJson("/api/runelite/submissions/submit", Map.of("submissionId", id), Map.class, callback);
	}

	public void getSubmissions(String player, ResultCallback<ZweetnekkenModels.SubmissionSummary[]> callback)
	{
		get("/api/runelite/submissions?player=" + encode(player), true, ZweetnekkenModels.SubmissionSummary[].class, callback);
	}

	private <T> void get(String path, boolean authenticated, Class<T> type, ResultCallback<T> callback)
	{
		execute(newRequest(path, authenticated).get().build(), type, callback);
	}

	private <T> void postJson(String path, Object value, Class<T> type, ResultCallback<T> callback)
	{
		postJsonWithMethod(path, value, "POST", type, callback);
	}

	private <T> void postJsonWithMethod(String path, Object value, String method, Class<T> type, ResultCallback<T> callback)
	{
		String json = gson.toJson(value);
		if ("/api/runelite/player-progress".equals(path))
		{
			if (json.contains("\"name\":null") || json.contains("\"name\":\"\""))
			{
				log.error("Player-progress payload contains a null or blank name before POST");
			}
		}
		RequestBody body = RequestBody.create(JSON, json);
		execute(newRequest(path, true).method(method, body).build(), type, callback);
	}

	private Request.Builder newRequest(String path, boolean authenticated)
	{
		Request.Builder builder = new Request.Builder().url(baseUrl() + path)
			.header("Accept", "application/json")
			.header("X-Zweetnekken-Plugin-Version", PLUGIN_VERSION);
		if (authenticated)
		{
			String code = config.authenticationCode() == null ? "" : config.authenticationCode().trim();
			if (!code.isEmpty()) { builder.header("Authorization", "Bearer " + code); }
		}
		return builder;
	}

	private <T> void execute(Request request, Class<T> type, ResultCallback<T> callback)
	{
		http.newCall(request).enqueue(new Callback()
		{
			@Override public void onFailure(Call call, IOException e)
			{
				log.error("Zweetnekken API request failed: {} {}", request.method(), request.url().encodedPath(), e);
				callback.complete(ApiResult.error(0, "Network unavailable"));
			}
			@Override public void onResponse(Call call, Response response)
			{
				String body = "";
				try (Response ignored = response)
				{
					body = response.body() == null ? "" : response.body().string();
					if (!response.isSuccessful() && response.code() != 409)
					{
						log.error("Zweetnekken API returned HTTP {} for {} {}: {}", response.code(), request.method(),
							request.url().encodedPath(), responsePreview(body));
						callback.complete(ApiResult.error(response.code(), safeError(response.code())));
						return;
					}
					T parsed = body.isBlank() ? null : gson.fromJson(body, type);
					callback.complete(ApiResult.ok(response.code() == 409 ? 200 : response.code(), parsed));
				}
				catch (IOException | RuntimeException e)
				{
					log.error("Invalid Zweetnekken API response for {} {} (HTTP {}, content-type {}): {}", request.method(),
						request.url().encodedPath(), response.code(), response.header("Content-Type"), responsePreview(body), e);
					callback.complete(ApiResult.error(0, "Invalid server response"));
				}
			}
		});
	}

	private String responsePreview(String body)
	{
		if (body == null || body.isBlank()) return "<empty body>";
		String safe = body.replace('\r', ' ').replace('\n', ' ');
		String code = config.authenticationCode() == null ? "" : config.authenticationCode().trim();
		if (!code.isEmpty()) safe = safe.replace(code, "[redacted]");
		return safe.length() <= 2_000 ? safe : safe.substring(0, 2_000) + "… [truncated]";
	}

	private String baseUrl()
	{
		return origin;
	}

	private static String encode(String value) { return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20"); }
	private static String safeError(int status)
	{
		if (status == 401 || status == 403) return "Authentication code is invalid";
		if (status == 422) return "The server rejected this draft";
		if (status == 429) return "The server is busy; retry queued";
		return status >= 500 ? "Zweetnekken is temporarily unavailable" : "Request failed (HTTP " + status + ")";
	}
}
