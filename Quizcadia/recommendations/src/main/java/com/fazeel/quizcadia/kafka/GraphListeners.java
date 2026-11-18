package com.fazeel.quizcadia.kafka;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fazeel.quizcadia.domain.Events.AdaptiveResult;
import com.fazeel.quizcadia.domain.Events.GameCard;
import com.fazeel.quizcadia.domain.Events.MultiPlayerResult;
import com.fazeel.quizcadia.domain.Events.SinglePlayerResult;
import com.fazeel.quizcadia.domain.Events.UserRegistered;
import com.fazeel.quizcadia.graph.RecommendationGraph;
import com.fazeel.quizcadia.recommend.GameMode;

/** Keeps the recommendation graph in sync with games, users and results from the other services. */
@Component
@ConditionalOnProperty(name = "quizcadia.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class GraphListeners {

	private static final String TYPE = "spring.json.value.default.type=com.fazeel.quizcadia.domain.Events$";

	private final RecommendationGraph graph;

	public GraphListeners(RecommendationGraph graph) {
		this.graph = graph;
	}

	@KafkaListener(topics = "${quizcadia.topics.game-created}", properties = TYPE + "GameCard")
	public void game(GameCard g) {
		graph.upsertGame(g.gameId(), g.gameName(), g.gameImage(), g.categoryId(), GameMode.SINGLE);
	}

	@KafkaListener(topics = "${quizcadia.topics.multiplayer-game-created}", properties = TYPE + "GameCard")
	public void multiGame(GameCard g) {
		graph.upsertGame(g.gameId(), g.gameName(), g.gameImage(), g.categoryId(), GameMode.MULTI);
	}

	@KafkaListener(topics = "${quizcadia.topics.game-deleted}", properties = "spring.json.value.default.type=java.lang.Integer")
	public void deleted(Integer gameId) {
		graph.deleteGame(gameId);
	}

	@KafkaListener(topics = "${quizcadia.topics.user-registered}", properties = TYPE + "UserRegistered")
	public void user(UserRegistered u) {
		graph.upsertUser(u.userId(), u.userName());
	}

	@KafkaListener(topics = "${quizcadia.topics.single-player-result}", properties = TYPE + "SinglePlayerResult")
	public void single(SinglePlayerResult r) {
		graph.recordPlay(r.userId(), r.gameId(), playId("single", r.sessionId()), Instant.now());
	}

	@KafkaListener(topics = "${quizcadia.topics.adaptive-result}", properties = TYPE + "AdaptiveResult")
	public void adaptive(AdaptiveResult r) {
		graph.recordPlay(r.userId(), r.gameId(), playId("adaptive", r.sessionId()), Instant.now());
	}

	@KafkaListener(topics = "${quizcadia.topics.multiplayer-result}", properties = TYPE + "MultiPlayerResult")
	public void multi(MultiPlayerResult r) {
		if (r.standings() != null) {
			r.standings().forEach(s -> graph.recordPlay(s.userId(), r.gameId(), playId("multi", r.matchId()), Instant.now()));
		}
	}

	/** Names one finished game, so a repeated delivery is counted once. Without an id (an old message): always new. */
	static String playId(String mode, String id) {
		return id == null || id.isBlank() ? mode + ":" + java.util.UUID.randomUUID() : mode + ":" + id;
	}
}
