package com.fazeel.quizcadia.push;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fazeel.quizcadia.progress.ProgressService;
import com.fazeel.quizcadia.progress.Streaks.Streak;

/**
 * Every few minutes: players whose streak is alive but who haven't played today (UTC) get one push, at the time
 * {@link ReminderTimes} picks for their time zone. At most one a day per browser; never in the last half hour
 * (too late to be useful); subscriptions the push service has forgotten are removed.
 */
@Component
public class StreakReminders {

	private static final Logger log = LoggerFactory.getLogger(StreakReminders.class);
	static final Duration TOO_LATE = Duration.ofMinutes(30);
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US);

	private final PushSubscriptionRepository subscriptions;
	private final ProgressService progress;
	private final ObjectProvider<PushSender> sender;
	private final Clock clock;

	public StreakReminders(PushSubscriptionRepository subscriptions, ProgressService progress, ObjectProvider<PushSender> sender,
			Clock clock) {
		this.subscriptions = subscriptions;
		this.progress = progress;
		this.sender = sender;
		this.clock = clock;
	}

	@Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT5M")
	public void run() {
		PushSender push = sender.getIfAvailable();
		if (push != null) {
			int sent = remindDue(push, clock.instant());
			if (sent > 0) {
				log.info("Sent {} streak reminders", sent);
			}
		}
	}

	/** @return how many reminders went out */
	int remindDue(PushSender push, Instant now) {
		LocalDate day = now.atZone(ZoneOffset.UTC).toLocalDate();
		Instant deadline = ReminderTimes.deadline(day);
		int sent = 0;
		for (PushSubscription s : subscriptions.findAll()) {
			if (day.equals(s.lastRemindedDay())) {
				continue;
			}
			ZoneId zone = zone(s.timeZone());
			Optional<Instant> at = ReminderTimes.reminderAt(day, zone);
			if (at.isEmpty() || now.isBefore(at.get()) || !now.isBefore(deadline.minus(TOO_LATE))) {
				continue;
			}
			Streak streak = progress.progress(s.userId()).streak();
			if (streak.current() == 0 || streak.playedToday()) {
				subscriptions.save(s.remindedOn(day)); // nothing at stake today: don't look again until tomorrow
				continue;
			}
			try {
				WebPush.Result r = push.send(s.endpoint(), s.p256dh(), s.auth(), message(streak.current(), deadline, zone),
						Duration.between(now, deadline));
				if (r.gone()) {
					subscriptions.deleteById(s.endpoint()); // the player turned notifications off or the browser is gone
					continue;
				}
				if (r.ok()) {
					sent++;
				}
				else {
					log.warn("Push service answered {} for user {}", r.status(), s.userId());
				}
			}
			catch (java.io.IOException e) {
				log.warn("Could not reach the push service for user {}: {}", s.userId(), e.getMessage());
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return sent;
			}
			subscriptions.save(s.remindedOn(day)); // once a day, even after a failed try: never a flood of retries
		}
		return sent;
	}

	/** The notification, as the service worker shows it (title, body, page to open, one per tag). */
	static String message(int days, Instant deadline, ZoneId zone) {
		String until = TIME.format(deadline.atZone(zone));
		return "{\"title\":\"🔥 Keep your " + days + "-day streak\",\"body\":\"One quick game before " + until
				+ " keeps it alive.\",\"url\":\"/daily\",\"tag\":\"streak\"}";
	}

	static ZoneId zone(String name) {
		try {
			return name == null || name.isBlank() ? ZoneOffset.UTC : ZoneId.of(name);
		}
		catch (java.time.DateTimeException e) {
			return ZoneOffset.UTC;
		}
	}
}
