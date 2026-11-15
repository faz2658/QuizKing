package com.fazeel.quizking.arena;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fazeel.quizking.arena.ArenaService.ArenaView;
import com.fazeel.quizking.arena.ArenaService.Phase;
import com.fazeel.quizking.daily.DailyChallengeServiceTest;
import com.fazeel.quizking.domain.SinglePlayerResult;
import com.fazeel.quizking.kafka.GameEvents;
import com.fazeel.quizking.repository.GameRepository;
import com.fazeel.quizking.session.QuizRuleException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ArenaServiceTest {

	static class MutableClock extends Clock {
		Instant now;

		MutableClock(Instant now) {
			this.now = now;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}

	static final Instant START = Instant.parse("2026-11-15T18:15:00Z");

	@Mock
	ArenaRoundRepository rounds;
	@Mock
	ArenaEntryRepository entries;
	@Mock
	ArenaPublishedRepository published;
	@Mock
	GameRepository games;
	@Mock
	GameEvents events;

	MutableClock clock = new MutableClock(START.minusSeconds(120));
	Map<String, ArenaRound> roundStore = new HashMap<>();
	Map<String, ArenaEntry> entryStore = new HashMap<>();
	Set<String> publishedStore = new HashSet<>();
	ArenaService arena;

	@BeforeEach
	void setUp() {
		arena = new ArenaService(rounds, entries, published, games, events, clock);
		when(games.findAll()).thenReturn(DailyChallengeServiceTest.catalog());
		when(rounds.findById(anyString())).thenAnswer(i -> Optional.ofNullable(roundStore.get((String) i.getArgument(0))));
		when(rounds.insert(any(ArenaRound.class))).thenAnswer(i -> {
			ArenaRound r = i.getArgument(0);
			roundStore.put(r.id(), r);
			return r;
		});
		when(entries.findById(anyString())).thenAnswer(i -> Optional.ofNullable(entryStore.get((String) i.getArgument(0))));
		when(entries.save(any(ArenaEntry.class))).thenAnswer(i -> {
			ArenaEntry e = i.getArgument(0);
			entryStore.put(e.getId(), e);
			return e;
		});
		when(entries.findByRoundId(anyString())).thenAnswer(i -> entryStore.values().stream()
				.filter(e -> e.getRoundId().equals(i.getArgument(0))).toList());
		when(published.existsById(anyString())).thenAnswer(i -> publishedStore.contains((String) i.getArgument(0)));
		when(published.insert(any(ArenaPublished.class))).thenAnswer(i -> {
			publishedStore.add(((ArenaPublished) i.getArgument(0)).roundId());
			return i.getArgument(0);
		});
	}

	String correct(int index) {
		return roundStore.get(START.toString()).questions().get(index).getCorrectAnswer();
	}

	void at(Duration sinceStart) {
		clock.now = START.plus(sinceStart);
	}

	@Test
	void lastOneStandingWins() {
		ArenaView before = arena.join(1, "Ada", "CA");
		arena.join(2, "Bo", "IN");
		arena.join(3, "Cy", "BR");
		assertThat(before.round().phase()).isEqualTo(Phase.JOINING);
		assertThat(arena.view(1).round().players()).isEqualTo(3);

		// question 1: Ada and Bo right, Cy wrong
		at(Duration.ofSeconds(3));
		ArenaView live = arena.view(1);
		assertThat(live.round().phase()).isEqualTo(Phase.LIVE);
		assertThat(live.round().question().number()).isEqualTo(1);
		arena.answer(1, 0, correct(0));
		arena.answer(2, 0, correct(0));
		arena.answer(3, 0, "nope");

		// reveal of question 1: Cy is out
		at(Duration.ofSeconds(17));
		ArenaView reveal = arena.view(3);
		assertThat(reveal.round().reveal().correctAnswer()).isEqualTo(correct(0));
		assertThat(reveal.round().you().alive()).isFalse();
		assertThat(reveal.round().alive()).isEqualTo(2);

		// question 2: only Ada answers right; Cy can't answer any more
		at(ArenaRules.SLOT.plusSeconds(2));
		assertThatThrownBy(() -> arena.answer(3, 1, correct(1))).isInstanceOf(QuizRuleException.class).hasMessageContaining("out");
		arena.answer(1, 1, correct(1));

		// after question 2 closes only Ada is left: the round is over
		at(ArenaRules.SLOT.multipliedBy(2).plusSeconds(1));
		ArenaView done = arena.view(1);
		assertThat(done.previous().phase()).isEqualTo(Phase.FINISHED);
		assertThat(done.previous().standings()).extracting(ArenaService.Standing::name).containsExactly("Ada", "Bo", "Cy");
		assertThat(done.previous().standings().get(0).country()).isEqualTo("CA");
		assertThat(done.previous().you().rank()).isEqualTo(1);
		assertThat(done.round().phase()).isIn(Phase.SOON, Phase.JOINING); // the next round is up

		ArgumentCaptor<SinglePlayerResult> results = ArgumentCaptor.forClass(SinglePlayerResult.class);
		verify(events, times(3)).publishResult(results.capture());
		assertThat(results.getAllValues()).allMatch(r -> "ARENA".equals(r.variant()) && r.players() == 3);
		assertThat(results.getAllValues().get(0).rank()).isEqualTo(1);
		arena.view(2); // asking again doesn't publish twice
		verify(events, times(3)).publishResult(any());
	}

	@Test
	void joiningAndAnsweringFollowTheClock() {
		clock.now = START.minus(ArenaRules.JOIN_BEFORE).minusSeconds(30);
		assertThatThrownBy(() -> arena.join(1, "Ada", null)).hasMessageContaining("10 minutes");

		clock.now = START.minusSeconds(60);
		arena.join(1, "Ada", null);
		assertThatThrownBy(() -> arena.answer(1, 0, "x")).hasMessageContaining("not in this round"); // the round hasn't started

		at(Duration.ofSeconds(5));
		arena.answer(1, 0, correct(0));
		assertThatThrownBy(() -> arena.answer(1, 0, correct(0))).hasMessageContaining("already");
		assertThatThrownBy(() -> arena.answer(1, 1, correct(1))).hasMessageContaining("isn't open");
	}

	@Test
	void fasterAnswersEarnMorePoints() {
		assertThat(ArenaRules.points(START, START.plusSeconds(1))).isGreaterThan(ArenaRules.points(START, START.plusSeconds(10)));
		assertThat(ArenaRules.points(START, START.plusSeconds(15))).isEqualTo(ArenaRules.BASE_POINTS);
		assertThat(ArenaRules.roundStart(Instant.parse("2026-11-15T18:29:59Z"))).isEqualTo(START);
	}
}
