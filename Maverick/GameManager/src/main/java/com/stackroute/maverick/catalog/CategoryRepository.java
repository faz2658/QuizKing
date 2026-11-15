package com.stackroute.maverick.catalog;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.stackroute.maverick.domain.Category;

public interface CategoryRepository extends MongoRepository<Category, Integer> {
}
