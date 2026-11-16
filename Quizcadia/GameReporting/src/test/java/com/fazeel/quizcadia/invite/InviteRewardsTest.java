package com.fazeel.quizcadia.invite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import com.fazeel.quizcadia.domain.UserRegistered;
import com.fazeel.quizcadia.progress.Achievements;
import com.fazeel.quizcadia.progress.Achievements.Badge;
import com.fazeel.quizcadia.progress.Streaks;

@ExtendWith(MockitoExtension.class)
class InviteRewardsTest {

	static final LocalDate TODAY = LocalDate.parse("2026-11-15");

	@Mock
	ReferralRepository referrals;

	Invites invites() {
		return new Invites(referrals, Clock.fixed(Instant.parse("2026-11-15T12:00:00Z"), ZoneOffset.UTC));
	}

	@Test
	void recordsAFriendOnceAndIgnoresSignUpsWithoutAnInvite() {
		Invites invites = invites();
		invites.registered(new UserRegistered(9, "Bo", "bo@x.test", "USER", Instant.parse("2026-11-15T10:00:00Z"), 4));
		verify(referrals).insert(new Referral(9, "Bo", 4, Instant.parse("2026-11-15T10:00:00Z")));

		when(referrals.insert(any(Referral.class))).thenThrow(new DuplicateKeyException("again"));
		invites.registered(new UserRegistered(9, "Bo", "bo@x.test", "USER", Instant.parse("2026-11-15T10:00:00Z"), 4)); // no error

		invites.registered(new UserRegistered(10, "Cy", "cy@x.test", "USER", Instant.now(), null));
		invites.registered(new UserRegistered(11, "Di", "di@x.test", "USER", Instant.now(), 11));
		verify(referrals, never()).insert(new Referral(10, "Cy", 0, Instant.now()));
	}

	@Test
	void bothSidesGetAShield() {
		when(referrals.findByReferrerIdOrderByJoinedAtAsc(4)).thenReturn(List.of(new Referral(9, "Bo", 4, Instant.now()),
				new Referral(10, "Cy", 4, Instant.now())));
		when(referrals.existsById(4)).thenReturn(true);
		assertThat(invites().shields(4)).isEqualTo(3);
		assertThat(Invites.code(1295)).isEqualTo("zz");
	}

	@Test
	void aShieldCoversOneMissedDayInTheCurrentStreak() {
		List<LocalDate> days = List.of(TODAY.minusDays(4), TODAY.minusDays(3), TODAY.minusDays(1)); // missed the 13th
		assertThat(Streaks.of(days, TODAY).current()).isEqualTo(1);

		Streaks.Streak shielded = Streaks.of(days, TODAY, 1);
		assertThat(shielded.current()).isEqualTo(3);
		assertThat(shielded.shields()).isZero();
		assertThat(shielded.best()).isEqualTo(3);

		assertThat(Streaks.of(List.of(TODAY.minusDays(3)), TODAY, 5).current()).isZero(); // two missed days in a row
		assertThat(Streaks.of(List.of(), TODAY, 2).shields()).isEqualTo(2);
	}

	@Test
	void invitingEarnsRecruiterThenAmbassador() {
		List<Instant> joined = List.of(Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-02T00:00:00Z"));
		Map<Badge, Achievements.Achievement> got = new java.util.EnumMap<>(Badge.class);
		Achievements.of(List.of(), r -> 0, joined).forEach(a -> got.put(a.id(), a));

		assertThat(got.get(Badge.RECRUITER).unlockedAt()).isEqualTo(joined.get(0));
		assertThat(got.get(Badge.AMBASSADOR).progress()).isEqualTo(2);
		assertThat(got.get(Badge.AMBASSADOR).unlockedAt()).isNull();
	}
}
