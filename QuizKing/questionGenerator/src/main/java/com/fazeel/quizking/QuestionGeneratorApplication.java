package com.fazeel.quizking;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.fazeel.quizking.bank.QuestionBankService;
import com.fazeel.quizking.generator.FactQuestionGenerator;
import com.fazeel.quizking.opentdb.OpenTriviaImporter;
import com.fazeel.quizking.opentdb.TriviaMapper;
import com.fazeel.quizking.opentdb.TriviaSource;

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
			@Value("${quizking.opentdb.request-spacing:5500ms}") Duration spacing) {
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
			@Value("${quizking.opentdb.import-on-start:false}") boolean importOnStart) {
		return args -> {
			if (importOnStart && !bank.hasQuestionsOfType("imported")) {
				log.info("Question bank has no imported questions: importing Open Trivia DB in the background");
				importer.start();
			}
		};
	}
}
