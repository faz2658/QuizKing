package com.fazeel.maverick.graph;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import com.fazeel.maverick.recommend.Candidate;
import com.fazeel.maverick.recommend.GameMode;

/**
 * All graph access. Writes use MERGE so replayed Kafka events are harmless.
 * Graph: (:User)-[:PLAYED {count, lastPlayedAt}]->(:Game)-[:IN_CATEGORY]->(:Category)<-[:FAVORS]-(:User)
 */
@Repository
public class RecommendationGraph {

	private final Neo4jClient neo4j;

	public RecommendationGraph(Neo4jClient neo4j) {
		this.neo4j = neo4j;
	}

	public void upsertGame(int gameId, String name, String image, int categoryId, GameMode mode) {
		neo4j.query("""
				MERGE (g:Game {gameId: $gameId})
				SET g.name = $name, g.image = $image, g.categoryId = $categoryId, g.mode = $mode
				MERGE (c:Category {categoryId: $categoryId})
				WITH g, c
				OPTIONAL MATCH (g)-[old:IN_CATEGORY]->(other:Category) WHERE other <> c
				DELETE old
				MERGE (g)-[:IN_CATEGORY]->(c)
				""").bindAll(Map.of("gameId", gameId, "name", nz(name), "image", nz(image), "categoryId", categoryId,
				"mode", mode.name())).run();
	}

	public void deleteGame(int gameId) {
		neo4j.query("MATCH (g:Game {gameId: $gameId}) DETACH DELETE g").bind(gameId).to("gameId").run();
	}

	public void upsertUser(int userId, String name) {
		neo4j.query("MERGE (u:User {userId: $userId}) SET u.name = $name")
				.bindAll(Map.of("userId", userId, "name", nz(name))).run();
	}

	public void recordPlay(int userId, int gameId, Instant at) {
		neo4j.query("""
				MERGE (u:User {userId: $userId})
				MERGE (g:Game {gameId: $gameId})
				MERGE (u)-[p:PLAYED]->(g)
				ON CREATE SET p.count = 1, p.lastPlayedAt = $at
				ON MATCH SET p.count = p.count + 1, p.lastPlayedAt = $at
				""").bindAll(Map.of("userId", userId, "gameId", gameId, "at", at.toString())).run();
	}

	public void setFavoriteCategories(int userId, Collection<Integer> categoryIds) {
		neo4j.query("""
				MERGE (u:User {userId: $userId})
				WITH u
				OPTIONAL MATCH (u)-[f:FAVORS]->(:Category)
				DELETE f
				WITH DISTINCT u
				UNWIND $categoryIds AS cid
				MERGE (c:Category {categoryId: cid})
				MERGE (u)-[:FAVORS]->(c)
				""").bindAll(Map.of("userId", userId, "categoryIds", List.copyOf(categoryIds))).run();
	}

	public List<Integer> favoriteCategories(int userId) {
		return List.copyOf(neo4j.query("MATCH (:User {userId: $userId})-[:FAVORS]->(c:Category) RETURN c.categoryId AS id")
				.bind(userId).to("userId").fetchAs(Integer.class).mappedBy((t, r) -> r.get("id").asInt()).all());
	}

	public List<Candidate> candidates(int userId) {
		return List.copyOf(neo4j.query("""
				MATCH (g:Game) WHERE g.name IS NOT NULL
				OPTIONAL MATCH (:User)-[p:PLAYED]->(g)
				WITH g, coalesce(sum(p.count), 0) AS totalPlays
				OPTIONAL MATCH (:User {userId: $userId})-[mine:PLAYED]->(g)
				WITH g, totalPlays, coalesce(mine.count, 0) AS myPlays
				OPTIONAL MATCH (g)-[:IN_CATEGORY]->(c:Category)<-[:FAVORS]-(:User {userId: $userId})
				WITH g, totalPlays, myPlays, count(c) > 0 AS favorite
				OPTIONAL MATCH (:User {userId: $userId})-[:PLAYED]->(:Game)<-[:PLAYED]-(other:User)-[:PLAYED]->(g)
				WHERE other.userId <> $userId
				RETURN g.gameId AS gameId, g.name AS name, g.image AS image, g.categoryId AS categoryId,
				       g.mode AS mode, totalPlays, myPlays, favorite, count(DISTINCT other) AS similar
				""").bind(userId).to("userId").fetchAs(Candidate.class).mappedBy((t, r) -> new Candidate(
				r.get("gameId").asInt(), r.get("name").asString(null), r.get("image").asString(null),
				r.get("categoryId").asInt(0), GameMode.valueOf(r.get("mode").asString("SINGLE")),
				r.get("totalPlays").asLong(), r.get("myPlays").asLong(), r.get("favorite").asBoolean(),
				r.get("similar").asLong())).all());
	}

	private static String nz(String s) {
		return s == null ? "" : s;
	}
}
