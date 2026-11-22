package com.fazeel.quizcadia.web;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Makes writes safe to send twice. A POST, PUT, PATCH or DELETE that carries an {@code Idempotency-Key} header
 * (a fresh random id per action, the same id on every retry of it) is carried out once; sending it again with
 * the same key gets the first answer back, status, body and cookies included, instead of doing it again. So a
 * player whose connection dropped after "send answer" or "create duel" can retry without the answer being refused
 * as already given, or the duel sent twice. The app adds the header to every write (see idempotency.interceptor.ts).
 *
 * <ul>
 * <li>A retry that arrives while the first is still running waits for it (up to {@link #WAIT_FOR_FIRST}).</li>
 * <li>Only answers below 500 are remembered: after a server error the retry runs again.</li>
 * <li>Keys belong to the signed-in player (or, before sign-in, to the key alone), and the same key with a different
 * body is refused with 422, so a key can't be reused for something else.</li>
 * <li>Answers are kept in memory for {@link #KEEP}: each service runs as one instance. A retry after the service
 * restarted runs again; the engines still refuse to count an answer twice.</li>
 * </ul>
 *
 * Requests without the header behave exactly as before. Runs after Spring Security, so a replay never skips
 * authentication.
 */
@Component
public class IdempotencyFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);

	public static final String HEADER = "Idempotency-Key";
	static final Duration KEEP = Duration.ofMinutes(10);
	static final Duration WAIT_FOR_FIRST = Duration.ofSeconds(20);
	static final int MAX_ENTRIES = 10_000;
	private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9_-]{8,128}");
	private static final Set<String> WRITES = Set.of("POST", "PUT", "PATCH", "DELETE");
	/** Response headers worth replaying; the rest (dates, security headers) are written fresh. */
	private static final List<String> REPLAYED_HEADERS = List.of(HttpHeaders.SET_COOKIE, HttpHeaders.LOCATION,
			HttpHeaders.RETRY_AFTER, HttpHeaders.CACHE_CONTROL);

	/** A finished answer, ready to send again. */
	record Stored(String fingerprint, int status, String contentType, byte[] body, Map<String, List<String>> headers,
			Instant at) {
	}

	/** One key's state: running until {@code result} completes (null when the answer is not to be remembered). */
	private record Entry(String fingerprint, CompletableFuture<Stored> result, Instant at) {
	}

	private final Map<String, Entry> entries = new ConcurrentHashMap<>();
	private final AtomicInteger sinceCleanUp = new AtomicInteger();
	private final Clock clock;

	public IdempotencyFilter() {
		this(Clock.systemUTC());
	}

	IdempotencyFilter(Clock clock) {
		this.clock = clock;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !WRITES.contains(request.getMethod()) || request.getHeader(HEADER) == null;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String key = request.getHeader(HEADER);
		if (!VALID_KEY.matcher(key).matches()) {
			problem(response, HttpStatus.BAD_REQUEST, "The Idempotency-Key header must be 8 to 128 letters, digits, - or _.");
			return;
		}
		byte[] body = request.getInputStream().readAllBytes(); // read once here, handed on by ReplayableBody
		String fingerprint = request.getMethod() + " " + request.getRequestURI() + "?" + request.getQueryString() + " "
				+ sha256(body);
		String scoped = owner() + ":" + key;

		Instant now = clock.instant();
		cleanUp(now);
		Entry mine = new Entry(fingerprint, new CompletableFuture<>(), now);
		Entry earlier = entries.putIfAbsent(scoped, mine);
		if (earlier != null && earlier.at().plus(KEEP).isBefore(now)) {
			entries.remove(scoped, earlier); // expired but not cleaned up yet
			earlier = entries.putIfAbsent(scoped, mine);
		}
		if (earlier != null) {
			replay(earlier, fingerprint, response);
			return;
		}

		ContentCachingResponseWrapper res = new ContentCachingResponseWrapper(response);
		Stored stored = null;
		try {
			// (no write endpoint answers asynchronously, so the answer is complete when the chain returns)
			chain.doFilter(new ReplayableBody(request, body), res);
			if (res.getStatus() < 500) {
				stored = new Stored(fingerprint, res.getStatus(), res.getContentType(), res.getContentAsByteArray(),
						headersOf(res), now);
			}
		}
		finally {
			if (stored == null) {
				entries.remove(scoped, mine); // a server error or an exception: let a retry run again
			}
			mine.result().complete(stored);
			res.copyBodyToResponse();
		}
	}

	private void replay(Entry earlier, String fingerprint, HttpServletResponse response) throws IOException {
		if (!earlier.fingerprint().equals(fingerprint)) {
			problem(response, HttpStatus.UNPROCESSABLE_CONTENT,
					"This Idempotency-Key was already used for a different request.");
			return;
		}
		Stored stored;
		try {
			stored = earlier.result().get(WAIT_FOR_FIRST.toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (TimeoutException e) {
			problem(response, HttpStatus.CONFLICT, "The same request is still being processed. Please try again shortly.");
			return;
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException(e);
		}
		catch (ExecutionException e) {
			stored = null;
		}
		if (stored == null) {
			// the first attempt failed and was forgotten in the meantime: ask the client to send it again
			problem(response, HttpStatus.CONFLICT, "The first attempt of this request failed. Please try again.");
			return;
		}
		log.debug("Replaying the answer for a repeated request ({} {})", stored.status(), fingerprint);
		response.setStatus(stored.status());
		if (stored.contentType() != null) {
			response.setContentType(stored.contentType());
		}
		stored.headers().forEach((name, values) -> values.forEach(v -> response.addHeader(name, v)));
		response.setHeader("Idempotent-Replayed", "true");
		response.setContentLength(stored.body().length);
		response.getOutputStream().write(stored.body());
	}

	private static Map<String, List<String>> headersOf(HttpServletResponse res) {
		Map<String, List<String>> headers = new LinkedHashMap<>();
		for (String name : REPLAYED_HEADERS) {
			var values = res.getHeaders(name);
			if (values != null && !values.isEmpty()) {
				headers.put(name, new ArrayList<>(values));
			}
		}
		return headers;
	}

	/** The signed-in player, so one player's key never finds another player's answer. */
	private static String owner() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		return auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName()) ? "u" + auth.getName()
				: "anon";
	}

	private void cleanUp(Instant now) {
		if (entries.size() < MAX_ENTRIES && sinceCleanUp.incrementAndGet() < 200) {
			return; // cheap most of the time
		}
		sinceCleanUp.set(0);
		entries.entrySet().removeIf(e -> e.getValue().result().isDone() && e.getValue().at().plus(KEEP).isBefore(now));
		if (entries.size() >= MAX_ENTRIES) {
			// a flood of keys: drop the oldest finished ones rather than run out of memory
			entries.entrySet().stream().filter(e -> e.getValue().result().isDone())
					.sorted(Comparator.comparing(e -> e.getValue().at()))
					.limit(entries.size() - MAX_ENTRIES / 2)
					.map(Map.Entry::getKey).toList().forEach(entries::remove);
		}
	}

	int size() {
		return entries.size();
	}

	private static void problem(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		String json = "{\"type\":\"about:blank\",\"title\":\"" + status.getReasonPhrase() + "\",\"status\":"
				+ status.value() + ",\"detail\":\"" + detail + "\"}";
		response.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
	}

	private static String sha256(byte[] body) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** The request with its body, already read for the fingerprint, readable again by the controller. */
	private static final class ReplayableBody extends HttpServletRequestWrapper {

		private final byte[] body;

		ReplayableBody(HttpServletRequest request, byte[] body) {
			super(request);
			this.body = body;
		}

		@Override
		public ServletInputStream getInputStream() {
			ByteArrayInputStream in = new ByteArrayInputStream(body);
			return new ServletInputStream() {
				@Override
				public int read() {
					return in.read();
				}

				@Override
				public int read(byte[] b, int off, int len) {
					return in.read(b, off, len);
				}

				@Override
				public boolean isFinished() {
					return in.available() == 0;
				}

				@Override
				public boolean isReady() {
					return true;
				}

				@Override
				public void setReadListener(ReadListener listener) {
					throw new UnsupportedOperationException();
				}
			};
		}

		@Override
		public BufferedReader getReader() {
			return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
		}
	}
}
