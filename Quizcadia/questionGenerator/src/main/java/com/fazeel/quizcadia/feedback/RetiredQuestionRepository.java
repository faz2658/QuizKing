package com.fazeel.quizcadia.feedback;

import org.springframework.data.mongodb.repository.MongoRepository;

/** Retired questions in MongoDB, by question id. */
public interface RetiredQuestionRepository extends MongoRepository<RetiredQuestion, Integer> {
}
