package com.fazeel.quizcadia.challenge;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fazeel.quizcadia.session.SessionQuestion;

/**
 * A challenge between two friends. The challenger picks a game (or its International version) and shares the link;
 * the first other player to accept it becomes the opponent. The questions are drawn once, at that moment, so both
 * players' countries are known, and then both play exactly those questions whenever they like until
 * {@link #expiresAt}. The higher score wins.
 *
 * @param status PENDING until someone accepts or declines; ACCEPTED or DECLINED after that (expiry is worked out
 *               from {@code expiresAt}, see {@link FriendDuelService})
 * @param questions empty until accepted
 */
@Document(collection = "friend_duels")
public record FriendDuel(@Id String id, @Version Long version, int gameId, String gameName, int categoryId,
		boolean international, Side challenger, Side opponent, Status status, List<SessionQuestion> questions,
		Instant createdAt, Instant acceptedAt, Instant expiresAt) {

	public enum Status {
		PENDING, ACCEPTED, DECLINED
	}

	/**
	 * One player. {@code country} only decides which questions are drawn and is never shown to anyone;
	 * {@code sessionId} is set once they start playing.
	 */
	public record Side(@Indexed int userId, String name, String country, String sessionId) {

		Side playing(String session) {
			return new Side(userId, name, country, session);
		}
	}

	public boolean isChallenger(int userId) {
		return challenger.userId() == userId;
	}

	public boolean isOpponent(int userId) {
		return opponent != null && opponent.userId() == userId;
	}

	/** The side of {@code userId}, or null if they are not in it. */
	public Side sideOf(int userId) {
		return isChallenger(userId) ? challenger : isOpponent(userId) ? opponent : null;
	}

	/** The other player of {@code userId}, or null (also while nobody has accepted). */
	public Side otherSide(int userId) {
		return isChallenger(userId) ? opponent : isOpponent(userId) ? challenger : null;
	}

	FriendDuel acceptedBy(Side who, List<SessionQuestion> drawn, Instant at, Instant playUntil) {
		return new FriendDuel(id, version, gameId, gameName, categoryId, international, challenger, who,
				Status.ACCEPTED, List.copyOf(drawn), createdAt, at, playUntil);
	}

	FriendDuel declinedBy(Side who) {
		return new FriendDuel(id, version, gameId, gameName, categoryId, international, challenger, who,
				Status.DECLINED, List.of(), createdAt, null, expiresAt);
	}

	FriendDuel withSession(int userId, String sessionId) {
		Side c = isChallenger(userId) ? challenger.playing(sessionId) : challenger;
		Side o = isOpponent(userId) ? opponent.playing(sessionId) : opponent;
		return new FriendDuel(id, version, gameId, gameName, categoryId, international, c, o, status, questions,
				createdAt, acceptedAt, expiresAt);
	}
}
