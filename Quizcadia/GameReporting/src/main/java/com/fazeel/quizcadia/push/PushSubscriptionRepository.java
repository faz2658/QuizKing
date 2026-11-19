package com.fazeel.quizcadia.push;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface PushSubscriptionRepository extends MongoRepository<PushSubscription, String> {

	List<PushSubscription> findByUserId(int userId);

	long countByUserId(int userId);
}
