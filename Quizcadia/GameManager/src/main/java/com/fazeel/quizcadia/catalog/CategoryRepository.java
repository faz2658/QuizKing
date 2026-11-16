package com.fazeel.quizcadia.catalog;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.fazeel.quizcadia.domain.Category;

public interface CategoryRepository extends MongoRepository<Category, Integer> {
}
