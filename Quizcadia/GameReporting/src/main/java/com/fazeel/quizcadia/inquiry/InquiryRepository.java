package com.fazeel.quizcadia.inquiry;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface InquiryRepository extends MongoRepository<Inquiry, String> {

	List<Inquiry> findTop200ByOrderByCreatedAtDesc();
}
