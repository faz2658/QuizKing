package com.fazeel.quizcadia.match;

import java.util.Collection;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Which questions each player has seen in matches, so new matches prefer questions none of their players has seen.
 */
public interface SeenQuestionRepository extends MongoRepository<SeenQuestion, String> {

	List<SeenQuestion> findByUserIdInAndQuestionIdIn(Collection<Integer> userIds, Collection<Integer> questionIds);
}
