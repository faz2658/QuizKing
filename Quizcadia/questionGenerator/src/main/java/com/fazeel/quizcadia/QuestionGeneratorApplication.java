package com.fazeel.quizcadia;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.fazeel.quizcadia.bank.QuestionBankService;
import com.fazeel.quizcadia.generator.FactQuestionGenerator;
import com.fazeel.quizcadia.opentdb.OpenTriviaImporter;
import com.fazeel.quizcadia.opentdb.TriviaMapper;
import com.fazeel.quizcadia.opentdb.TriviaSource;
import com.fazeel.quizcadia.feedback.RetiredQuestionRepository;
import com.fazeel.quizcadia.pack.PackLoader;

@SpringBootApplication
public class QuestionGeneratorApplication {

	private static final Logger log = LoggerFactory.getLogger(QuestionGeneratorApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(QuestionGeneratorApplication.class, args);
	}

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	FactQuestionGenerator factQuestionGenerator() {
		return new FactQuestionGenerator(new SecureRandom());
	}

	@Bean(destroyMethod = "")
	OpenTriviaImporter openTriviaImporter(TriviaSource source, QuestionBankService bank,
			@Value("${quizcadia.opentdb.request-spacing:5500ms}") Duration spacing) {
		return new OpenTriviaImporter(source, new TriviaMapper(new SecureRandom()), bank,
				Executors.newSingleThreadExecutor(r -> {
					Thread t = new Thread(r, "opentdb-import");
					t.setDaemon(true);
					return t;
				}), d -> Thread.sleep(d), spacing, Clock.systemUTC());
	}

	/** With OPENTDB_IMPORT_ON_START=true, a bank without imported questions fills itself on startup. */
	@Bean
	ApplicationRunner importOnStart(OpenTriviaImporter importer, QuestionBankService bank,
			@Value("${quizcadia.opentdb.import-on-start:false}") boolean importOnStart) {
		return args -> {
			if (importOnStart && !bank.hasQuestionsOfType("imported")) {
				log.info("Question bank has no imported questions: importing Open Trivia DB in the background");
				importer.start();
			}
		};
	}

	/** Adds the bundled question packs (India &amp; South Asia, ...) to the bank on every start; already-loaded ones are kept. */
	@Bean
	ApplicationRunner loadPacks(QuestionBankService bank, ObjectProvider<RetiredQuestionRepository> retired,
			@Value("${quizcadia.packs.load-on-start:true}") boolean loadOnStart) {
		return args -> {
			if (!loadOnStart) {
				return;
			}
			try {
				RetiredQuestionRepository r = retired.getIfAvailable();
				int added = new PackLoader(bank, id -> r != null && r.existsById(id)).load(PackLoader.PACKS);
				log.info("Question packs: {} new questions", added);
			}
			catch (RuntimeException e) {
				log.warn("Could not load the question packs", e);
			}
		};
	}
}
