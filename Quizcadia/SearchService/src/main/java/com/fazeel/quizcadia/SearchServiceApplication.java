package com.fazeel.quizcadia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** The game search service: finds games by name through the game manager. */
@SpringBootApplication
public class SearchServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(SearchServiceApplication.class, args);
	}
}
