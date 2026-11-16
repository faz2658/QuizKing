package com.fazeel.quizcadia.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import com.fazeel.quizcadia.domain.Game;
import com.fazeel.quizcadia.repository.GameRepository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Loads a few sample games so the engine can be played without the rest of the platform. */
@Configuration
@ConditionalOnProperty(name = "quizcadia.demo-data", havingValue = "true")
public class DemoData {

	private static final Logger log = LoggerFactory.getLogger(DemoData.class);

	@Bean
	ApplicationRunner loadDemoGames(GameRepository games, ObjectMapper mapper) {
		return args -> {
			List<Game> demo = read(mapper);
			for (Game g : demo) {
				if (!games.existsById(g.getGameId())) {
					games.save(g);
				}
			}
			log.info("Demo data: {} sample games available", demo.size());
		};
	}

	static List<Game> read(ObjectMapper mapper) throws IOException {
		try (InputStream in = new ClassPathResource("demo-games.json").getInputStream()) {
			return mapper.readValue(in, new TypeReference<List<Game>>() {
			});
		}
	}
}
