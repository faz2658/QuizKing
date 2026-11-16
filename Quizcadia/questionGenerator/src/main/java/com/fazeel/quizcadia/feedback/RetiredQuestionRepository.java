package com.fazeel.quizcadia.feedback;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface RetiredQuestionRepository extends MongoRepository<RetiredQuestion, Integer> {
}
