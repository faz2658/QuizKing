package com.fazeel.quizcadia.bank;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.fazeel.quizcadia.domain.Category;

public interface CategoryRepository extends MongoRepository<Category, Integer> {

	Optional<Category> findByCategoryNameIgnoreCase(String name);
}
