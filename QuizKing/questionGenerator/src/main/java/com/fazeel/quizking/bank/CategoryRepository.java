package com.fazeel.quizking.bank;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.fazeel.quizking.domain.Category;

public interface CategoryRepository extends MongoRepository<Category, Integer> {

	Optional<Category> findByCategoryNameIgnoreCase(String name);
}
