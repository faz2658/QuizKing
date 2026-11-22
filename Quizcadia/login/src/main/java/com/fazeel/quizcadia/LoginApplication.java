package com.fazeel.quizcadia;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * The login service: accounts (email and password, or Google), guest play, sign-in sessions with a refresh cookie,
 * password resets, and the signed tokens every other service checks.
 */
@SpringBootApplication
public class LoginApplication {

	public static void main(String[] args) {
		SpringApplication.run(LoginApplication.class, args);
	}

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}
}
