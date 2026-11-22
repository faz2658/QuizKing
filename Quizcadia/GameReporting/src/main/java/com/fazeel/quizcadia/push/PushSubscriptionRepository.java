package com.fazeel.quizcadia.push;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

/** Browsers signed up for streak reminders. */
public interface PushSubscriptionRepository extends MongoRepository<PushSubscription, String> {

	List<PushSubscription> findByUserId(int userId);

	long countByUserId(int userId);
}
