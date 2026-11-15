package com.fazeel.quizking.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.fazeel.quizking.session.GameSession;
import com.fazeel.quizking.session.SessionVariant;

public interface GameSessionRepository extends MongoRepository<GameSession, String> {

	/** A player's run of one day's challenge or one friend challenge (there is at most one). */
	Optional<GameSession> findFirstByUserIdAndVariantAndVariantKey(int userId, SessionVariant variant, String variantKey);
}
