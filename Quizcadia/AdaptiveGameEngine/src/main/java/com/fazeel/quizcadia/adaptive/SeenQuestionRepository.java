package com.fazeel.quizcadia.adaptive;

import java.util.Collection;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

/** Which questions each player has already been asked, so new sessions can prefer unseen ones. */
public interface SeenQuestionRepository extends MongoRepository<SeenQuestion, String> {

	List<SeenQuestion> findByUserIdAndQuestionIdIn(int userId, Collection<Integer> questionIds);
}
