package com.stackroute.maverick.graph;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.neo4j.Neo4jContainer;

import com.stackroute.maverick.recommend.Candidate;
import com.stackroute.maverick.recommend.GameMode;

/** Runs the real Cypher against Neo4j. Skipped automatically when Docker is not available. */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class RecommendationGraphIT {

	@Container
	@ServiceConnection
	static Neo4jContainer neo4j = new Neo4jContainer("neo4j:5").withoutAuthentication();

	@Autowired
	RecommendationGraph graph;

	@Test
	void buildsCandidatesFromPlaysFavoritesAndSimilarPlayers() {
		graph.upsertGame(1, "Capitals", null, 10, GameMode.SINGLE);
		graph.upsertGame(2, "Rivers", null, 10, GameMode.SINGLE);
		graph.upsertGame(3, "Planets", null, 20, GameMode.MULTI);
		graph.setFavoriteCategories(100, Set.of(20));
		graph.recordPlay(100, 1, Instant.now());
		graph.recordPlay(200, 1, Instant.now());
		graph.recordPlay(200, 2, Instant.now());
		graph.recordPlay(200, 2, Instant.now());

		List<Candidate> cs = graph.candidates(100);

		assertThat(cs).hasSize(3);
		Candidate rivers = cs.stream().filter(c -> c.gameId() == 2).findFirst().orElseThrow();
		assertThat(rivers.totalPlays()).isEqualTo(2);
		assertThat(rivers.similarPlayers()).isEqualTo(1);
		assertThat(cs.stream().filter(c -> c.gameId() == 3).findFirst().orElseThrow().favoriteCategory()).isTrue();
		assertThat(cs.stream().filter(c -> c.gameId() == 1).findFirst().orElseThrow().myPlays()).isEqualTo(1);
		assertThat(graph.favoriteCategories(100)).containsExactly(20);

		graph.deleteGame(3);
		assertThat(graph.candidates(100)).hasSize(2);
	}
}
