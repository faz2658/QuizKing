package com.fazeel.quizcadia.inquiry;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

/** Requests from the events and venues forms. */
public interface InquiryRepository extends MongoRepository<Inquiry, String> {

	List<Inquiry> findTop200ByOrderByCreatedAtDesc();
}
