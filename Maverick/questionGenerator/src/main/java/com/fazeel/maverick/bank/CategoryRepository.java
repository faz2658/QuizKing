package com.fazeel.maverick.bank;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.fazeel.maverick.domain.Category;

public interface CategoryRepository extends MongoRepository<Category, Integer> {

	Optional<Category> findByCategoryNameIgnoreCase(String name);
}
