package com.fazeel.maverick.catalog;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.fazeel.maverick.domain.Category;

public interface CategoryRepository extends MongoRepository<Category, Integer> {
}
