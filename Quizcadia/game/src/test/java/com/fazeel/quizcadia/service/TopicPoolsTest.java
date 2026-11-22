package com.fazeel.quizcadia.service;

import static com.fazeel.quizcadia.session.TestGames.game;
import static com.fazeel.quizcadia.session.TestGames.question;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.fazeel.quizcadia.domain.Game;
import com.fazeel.quizcadia.domain.Questions;
import com.fazeel.quizcadia.domain.Topic;

class TopicPoolsTest {

	static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

	static Game topicGame(int topicId) {
		Game g = game(5, null);
		g.setQuestions(null);
		g.setTopic(new Topic(topicId, "History", null, null));
		return g;
	}

	@Test
	void aGameWithoutQuestionsGetsItsTopicsQuestions() {
		TopicPools pools = new TopicPools(id -> List.of(question(1, 1, "a", "a", "b")), Clock.fixed(T0, ZoneOffset.UTC),
				Duration.ofMinutes(10));

		assertThat(pools.withPool(topicGame(7)).getQuestions()).extracting(Questions::getQuestionId).containsExactly(1);
	}

	@Test
	void aGameCarryingItsOwnQuestionsKeepsThem() {
		AtomicInteger loads = new AtomicInteger();
		TopicPools pools = new TopicPools(id -> {
			loads.incrementAndGet();
			return List.of(question(9, 1, "a", "a", "b"));
		}, Clock.fixed(T0, ZoneOffset.UTC), Duration.ofMinutes(10));
		Game own = game(5, null, question(1, 1, "a", "a", "b"));
		own.setTopic(new Topic(7, "History", null, null));

		assertThat(pools.withPool(own).getQuestions()).extracting(Questions::getQuestionId).containsExactly(1);
		assertThat(loads).hasValue(0);
	}

	@Test
	void topicsAreLoadedOnceWhileFreshAndAgainAfterwards() {
		AtomicInteger loads = new AtomicInteger();
		List<Instant> now = new ArrayList<>(List.of(T0));
		Clock clock = new Clock() {
			public ZoneId getZone() { return ZoneOffset.UTC; }
			public Clock withZone(ZoneId z) { return this; }
			public Instant instant() { return now.get(0); }
		};
		TopicPools pools = new TopicPools(id -> {
			loads.incrementAndGet();
			return List.of(question(1, 1, "a", "a", "b"));
		}, clock, Duration.ofMinutes(10));

		pools.questions(7);
		pools.questions(7);
		assertThat(loads).hasValue(1);
		now.set(0, T0.plus(Duration.ofMinutes(11)));
		pools.questions(7);
		assertThat(loads).hasValue(2);
	}

	@Test
	void aFailedLoadIsNotKeptSoTheNextGameTriesAgain() {
		AtomicInteger loads = new AtomicInteger();
		TopicPools pools = new TopicPools(id -> loads.incrementAndGet() == 1 ? List.of() : List.of(question(1, 1, "a", "a", "b")),
				Clock.fixed(T0, ZoneOffset.UTC), Duration.ofMinutes(10));

		assertThat(pools.questions(7)).isEmpty();
		assertThat(pools.questions(7)).hasSize(1);
	}
}
