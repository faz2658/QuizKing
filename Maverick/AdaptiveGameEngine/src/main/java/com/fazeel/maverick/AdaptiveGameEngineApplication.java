package com.fazeel.maverick;

import java.security.SecureRandom;
import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

import com.fazeel.maverick.adaptive.PoolBuilder;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AdaptiveGameEngineApplication {

	public static void main(String[] args) {
		SpringApplication.run(AdaptiveGameEngineApplication.class, args);
	}

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	PoolBuilder poolBuilder() {
		return new PoolBuilder(new SecureRandom());
	}
}
