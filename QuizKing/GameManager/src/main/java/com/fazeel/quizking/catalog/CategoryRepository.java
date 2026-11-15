package com.fazeel.quizking.catalog;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.fazeel.quizking.domain.Category;

public interface CategoryRepository extends MongoRepository<Category, Integer> {
}
