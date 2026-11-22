package com.fazeel.quizcadia.push;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fazeel.quizcadia.auth.Callers;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A browser signs up for (or out of) streak reminders. The player is always the caller. */
@RestController
@RequestMapping("/api/v1/reports/push")
public class PushController {

	/** More than this many browsers for one player is abuse, not phones and laptops. */
	static final int MAX_PER_PLAYER = 10;
	static final Duration TEST_GAP = Duration.ofMinutes(1);

	private final PushSubscriptionRepository subscriptions;
	private final ObjectProvider<WebPush> push;
	private final Clock clock;
	private final Map<Integer, Instant> lastTest = new ConcurrentHashMap<>();

	public PushController(PushSubscriptionRepository subscriptions, ObjectProvider<WebPush> push, Clock clock) {
		this.subscriptions = subscriptions;
		this.push = push;
		this.clock = clock;
	}

	/** @param publicKey our VAPID key for {@code pushManager.subscribe}; null when reminders are switched off */
	public record KeyView(String publicKey) {
	}

	@GetMapping("/key")
	public KeyView key() {
		WebPush p = push.getIfAvailable();
		return new KeyView(p == null ? null : p.publicKey());
	}

	/** What {@code PushSubscription.toJSON()} gives, plus the browser's time zone (IANA, e.g. America/Toronto). */
	public record Subscribe(@NotBlank @Size(max = 2048) String endpoint, @NotBlank @Size(max = 200) String p256dh,
			@NotBlank @Size(max = 100) String auth, @Size(max = 64) String timeZone) {
	}

	public record Unsubscribe(@NotBlank @Size(max = 2048) String endpoint) {
	}

	@PostMapping("/subscriptions")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void subscribe(@Valid @RequestBody Subscribe s) {
		if (push.getIfAvailable() == null) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Reminders aren't switched on here");
		}
		if (!PushServices.allowed(s.endpoint())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That isn't a browser push service");
		}
		if (bytes(s.p256dh()) != 65 || bytes(s.auth()) != 16) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The subscription's keys aren't valid");
		}
		int me = Callers.currentUserId();
		boolean known = subscriptions.findById(s.endpoint()).map(x -> x.userId() == me).orElse(false);
		if (!known && subscriptions.countByUserId(me) >= MAX_PER_PLAYER) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Too many devices have reminders on");
		}
		String zone = StreakReminders.zone(s.timeZone()).getId();
		// the same browser signing in as someone else takes the subscription over
		subscriptions.save(new PushSubscription(s.endpoint(), me, s.p256dh(), s.auth(), zone, clock.instant(), null));
	}

	@DeleteMapping("/subscriptions")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void unsubscribe(@Valid @RequestBody Unsubscribe u) {
		int me = Callers.currentUserId();
		subscriptions.findById(u.endpoint()).filter(s -> s.userId() == me).ifPresent(s -> subscriptions.deleteById(s.endpoint()));
	}

	/** Sends the caller's browsers a test notification (at most once a minute). @return how many got it */
	@PostMapping("/test")
	public Map<String, Integer> test() throws IOException, InterruptedException {
		WebPush p = push.getIfAvailable();
		if (p == null) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Reminders aren't switched on here");
		}
		int me = Callers.currentUserId();
		Instant now = clock.instant();
		Instant last = lastTest.put(me, now);
		if (last != null && last.plus(TEST_GAP).isAfter(now)) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Wait a minute before another test");
		}
		int ok = 0;
		for (PushSubscription s : subscriptions.findByUserId(me)) {
			WebPush.Result r = p.send(s.endpoint(), s.p256dh(), s.auth(),
					"{\"title\":\"🔔 Reminders are on\",\"body\":\"We'll nudge you before your streak ends.\",\"url\":\"/\",\"tag\":\"test\"}",
					Duration.ofMinutes(10));
			if (r.gone()) {
				subscriptions.deleteById(s.endpoint());
			}
			else if (r.ok()) {
				ok++;
			}
		}
		return Map.of("delivered", ok);
	}

	private static int bytes(String b64url) {
		try {
			return Base64.getUrlDecoder().decode(b64url.replace('=', ' ').trim()).length;
		}
		catch (IllegalArgumentException e) {
			return -1;
		}
	}
}
