package com.fazeel.quizcadia.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.fazeel.quizcadia.progress.ProgressService;
import com.fazeel.quizcadia.progress.ProgressService.Progress;
import com.fazeel.quizcadia.progress.Streaks.Streak;

class StreakRemindersTest {

	static final LocalDate DAY = LocalDate.parse("2026-11-18");
	/** 21:00 UTC: reminder time in London (UTC); 4 pm in Toronto is 21:00 UTC too. */
	static final Instant NINE_PM_UTC = Instant.parse("2026-11-18T21:00:00Z");

	PushSubscriptionRepository repo = mock(PushSubscriptionRepository.class);
	ProgressService progress = mock(ProgressService.class);
	PushSender push = mock(PushSender.class);
	List<PushSubscription> saved = new ArrayList<>();
	StreakReminders reminders;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() throws Exception {
		reminders = new StreakReminders(repo, progress, mock(ObjectProvider.class), Clock.systemUTC());
		when(repo.save(any())).thenAnswer(i -> {
			saved.add(i.getArgument(0));
			return i.getArgument(0);
		});
		when(push.send(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(new WebPush.Result(201, true, false));
	}

	static PushSubscription sub(int user, String zone, LocalDate lastReminded) {
		return new PushSubscription("https://fcm.googleapis.com/fcm/send/" + user, user, "p", "a", zone, Instant.EPOCH, lastReminded);
	}

	void streak(int user, int current, boolean playedToday) {
		when(progress.progress(user)).thenReturn(new Progress(user, 0, 1, "", 0, 100, new Streak(current, current, playedToday), 3));
	}

	@Test
	void remindsAPlayerWhoseStreakIsAtRiskOnceThatDay() throws Exception {
		when(repo.findAll()).thenReturn(List.of(sub(1, "UTC", null)));
		streak(1, 5, false);

		assertThat(reminders.remindDue(push, NINE_PM_UTC)).isEqualTo(1);

		verify(push).send(eq("https://fcm.googleapis.com/fcm/send/1"), eq("p"), eq("a"),
				eq("{\"title\":\"🔥 Keep your 5-day streak\",\"body\":\"One quick game before 12:00 AM keeps it alive.\",\"url\":\"/daily\",\"tag\":\"streak\"}"),
				eq(java.time.Duration.ofHours(3)));
		assertThat(saved).singleElement().extracting(PushSubscription::lastRemindedDay).isEqualTo(DAY);
	}

	@Test
	void nobodyIsRemindedTwiceADayOrBeforeTheirTime() throws Exception {
		when(repo.findAll()).thenReturn(List.of(sub(1, "UTC", DAY), sub(2, "UTC", null)));
		streak(1, 5, false);
		streak(2, 5, false);

		assertThat(reminders.remindDue(push, NINE_PM_UTC.minusSeconds(60))).isZero(); // 8:59 pm: not yet for user 2
		verify(push, never()).send(anyString(), anyString(), anyString(), anyString(), any());
	}

	@Test
	void noReminderWhenThereIsNothingToLose() throws Exception {
		when(repo.findAll()).thenReturn(List.of(sub(1, "UTC", null), sub(2, "UTC", null)));
		streak(1, 0, false); // no streak
		streak(2, 4, true);  // already played today

		assertThat(reminders.remindDue(push, NINE_PM_UTC)).isZero();
		verify(push, never()).send(anyString(), anyString(), anyString(), anyString(), any());
		assertThat(saved).hasSize(2).allMatch(s -> DAY.equals(s.lastRemindedDay())); // not checked again today
	}

	@Test
	void nothingInTheLastHalfHour() throws Exception {
		when(repo.findAll()).thenReturn(List.of(sub(1, "UTC", null)));
		streak(1, 5, false);

		assertThat(reminders.remindDue(push, Instant.parse("2026-11-18T23:45:00Z"))).isZero();
	}

	@Test
	void forgetsBrowsersThePushServiceNoLongerKnows() throws Exception {
		when(repo.findAll()).thenReturn(List.of(sub(1, "UTC", null)));
		streak(1, 5, false);
		when(push.send(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(new WebPush.Result(410, false, true));

		reminders.remindDue(push, NINE_PM_UTC);

		verify(repo).deleteById("https://fcm.googleapis.com/fcm/send/1");
		assertThat(saved).isEmpty();
	}

	@Test
	void eachPlayerAtTheirOwnTime() throws Exception {
		when(repo.findAll()).thenReturn(List.of(sub(1, "America/Toronto", null), sub(2, "Asia/Kolkata", null)));
		streak(1, 3, false);
		streak(2, 3, false);

		// 15:30 UTC is 9 pm in India (its reminder) but only 10:30 am in Toronto (reminder at 4 pm)
		assertThat(reminders.remindDue(push, Instant.parse("2026-11-18T15:30:00Z"))).isEqualTo(1);
		verify(push).send(eq("https://fcm.googleapis.com/fcm/send/2"), anyString(), anyString(), anyString(), any());
		assertThat(StreakReminders.message(3, ReminderTimes.deadline(DAY), ZoneId.of("Asia/Kolkata"))).contains("before 5:30 AM");
	}

	@Test
	void anUnknownTimeZoneFallsBackToUtc() {
		assertThat(StreakReminders.zone("Not/AZone")).isEqualTo(java.time.ZoneOffset.UTC);
		assertThat(StreakReminders.zone(null)).isEqualTo(java.time.ZoneOffset.UTC);
	}
}
