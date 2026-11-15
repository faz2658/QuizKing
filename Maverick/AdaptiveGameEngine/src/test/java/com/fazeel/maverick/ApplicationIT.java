package com.fazeel.maverick;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import com.fazeel.maverick.adaptive.AdaptiveService;

/** Boots the whole application against a real MongoDB. Skipped when Docker is not available. */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class ApplicationIT {

	@Container
	@ServiceConnection
	static MongoDBContainer mongo = new MongoDBContainer("mongo:8.0");

	@Autowired
	AdaptiveService service;

	@Test
	void contextStarts() {
		assertThat(service).isNotNull();
	}
}
