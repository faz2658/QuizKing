package com.fazeel.quizcadia.challenge;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FriendDuelRepository extends MongoRepository<FriendDuel, String> {

	/** Challenges a player sent or accepted (or declined), newest first. */
	List<FriendDuel> findByChallengerUserIdOrOpponentUserIdOrderByCreatedAtDesc(int challengerId, int opponentId,
			Pageable page);
}
