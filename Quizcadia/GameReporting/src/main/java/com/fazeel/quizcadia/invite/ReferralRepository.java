package com.fazeel.quizcadia.invite;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ReferralRepository extends MongoRepository<Referral, Integer> {

	List<Referral> findByReferrerIdOrderByJoinedAtAsc(int referrerId);
}
