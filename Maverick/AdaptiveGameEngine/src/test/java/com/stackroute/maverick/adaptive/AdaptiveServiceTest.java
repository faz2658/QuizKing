package com.stackroute.maverick.adaptive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.stackroute.maverick.adaptive.Ports.GameSource;
import com.stackroute.maverick.adaptive.Ports.ResultEvents;
import com.stackroute.maverick.adaptive.Repositories.GameRepository;
import com.stackroute.maverick.adaptive.Repositories.SeenQuestionRepository;
import com.stackroute.maverick.adaptive.Repositories.SessionRepository;
import com.stackroute.maverick.domain.Game;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdaptiveServiceTest {

	@Mock
	SessionRepository sessions;
	@Mock
	GameRepository games;
	@Mock
	SeenQuestionRepository seen;
	@Mock
	GameSource source;
	@Mock
	ResultEvents events;

	Map<String, AdaptiveSession> store = new HashMap<>();
	AdaptiveService service;

	static Game game(int questions) {
		List<Game.Question> qs = new ArrayList<>();
		for (int i = 1; i <= questions; i++) {
			qs.add(new Game.Question(i, 1, "Q" + i, "right", "wrong", null, "", "right"));
		}
		qs.add(new Game.Question(99, 1, "broken", "a", "b", null, null, "c"));
		return new Game(5, "Adaptive", 1, new Game.Topic(7, "Science"), qs);
	}

	@BeforeEach
	void setUp() {
		service = new AdaptiveService(sessions, games, seen, source, events, new PoolBuilder(new Random(3)),
				new AdaptiveRules(4, 60, 30, 3, 20, 10), Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"),
						ZoneOffset.UTC));
		when(sessions.save(any())).thenAnswer(i -> {
			AdaptiveSession s = i.getArgument(0);
			store.put(s.getId(), s);
			return s;
		});
		when(sessions.findById(any())).thenAnswer(i -> Optional.ofNullable(store.get(i.getArgument(0))));
		when(games.save(any())).thenAnswer(i -> i.getArgument(0));
	}

	@Test
	void unseenQuestionsComeFirst() {
		when(games.findById(5)).thenReturn(Optional.of(game(4)));
		when(seen.findByUserIdAndGameId(9, 5))
				.thenReturn(List.of(SeenQuestion.of(9, 5, 1), SeenQuestion.of(9, 5, 2), SeenQuestion.of(9, 5, 3)));

		AdaptiveSession s = service.start(5, 9);

		assertThat(s.currentQuestion()).get().extracting(AskedQuestion::getQuestionId).isEqualTo(4);
	}

	@Test
	void gameIsFetchedFromTheGameManagerWhenNotCached() {
		when(games.findById(5)).thenReturn(Optional.empty());
		when(source.fetch(5)).thenReturn(Optional.of(game(4)));

		AdaptiveSession s = service.start(5, 9);

		assertThat(s.getTopicId()).isEqualTo(7);
		verify(games).save(any());
	}

	@Test
	void resultIsPublishedOnceAndIncludesEveryAnswer() {
		when(games.findById(5)).thenReturn(Optional.of(game(5)));
		AdaptiveSession s = service.start(5, 9);
		for (int i = 0; i < 3; i++) {
			int q = service.get(s.getId()).currentQuestion().orElseThrow().getQuestionId();
			service.answer(s.getId(), q, "right");
		}
		service.get(s.getId());

		verify(events, times(1)).sessionCompleted(org.mockito.ArgumentMatchers.argThat(r -> r.answers().size() == 3
				&& r.score() == 30 && r.userId() == 9 && Set.of(1, 2, 3, 4, 5).contains(r.answers().get(0).questionId())));
	}
}
