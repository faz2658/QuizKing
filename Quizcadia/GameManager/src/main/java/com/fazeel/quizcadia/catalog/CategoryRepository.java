package com.fazeel.quizcadia.catalog;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.fazeel.quizcadia.domain.Category;

/** The question bank's categories as the game manager keeps them (topics with their questions). */
public interface CategoryRepository extends MongoRepository<Category, Integer> {
}
