package com.fazeel.quizcadia.arena;

import org.springframework.data.mongodb.repository.MongoRepository;

/** Marks rounds whose results were published, so each is published once. */
public interface ArenaPublishedRepository extends MongoRepository<ArenaPublished, String> {
}
