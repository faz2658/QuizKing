package com.fazeel.quizcadia;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoOperations;

import com.fazeel.quizcadia.bank.QuestionBankService;
import com.fazeel.quizcadia.feedback.RetiredQuestionRepository;
import com.fazeel.quizcadia.generator.FactQuestionGenerator;
import com.fazeel.quizcadia.opentdb.OpenTriviaImporter;
import com.fazeel.quizcadia.opentdb.TriviaMapper;
import com.fazeel.quizcadia.opentdb.TriviaSource;
import com.fazeel.quizcadia.pack.PackLoader;

/**
 * The question bank service: categories, topics and questions in MongoDB, plus the Open Trivia DB importer, the
 * bundled question packs, Wikidata fact questions, AI drafts, AI topic quizzes and player feedback.
 */
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
			ObjectProvider<MongoOperations> mongo,
			@Value("${quizcadia.packs.load-on-start:true}") boolean loadOnStart) {
		return args -> {
			if (!loadOnStart) {
				return;
			}
			try {
				RetiredQuestionRepository r = retired.getIfAvailable();
				MongoOperations m = mongo.getIfAvailable();
				PackLoader.LoadedPacks loaded = m == null ? inMemoryPacks() : new PackLoader.LoadedPacks() {
					@Override
					public String hash(String pack) {
						Document d = m.findById(pack, Document.class, "question_packs");
						return d == null ? null : d.getString("hash");
					}

					@Override
					public void loaded(String pack, String hash) {
						m.save(new Document("_id", pack).append("hash", hash), "question_packs");
					}
				};
				int added = new PackLoader(bank, id -> r != null && r.existsById(id), loaded).load(PackLoader.bundled());
				log.info("Question packs: {} new questions", added);
			}
			catch (RuntimeException e) {
				log.warn("Could not load the question packs", e);
			}
		};
	}

	private static PackLoader.LoadedPacks inMemoryPacks() {
		Map<String, String> m = new ConcurrentHashMap<>();
		return new PackLoader.LoadedPacks() {
			@Override
			public String hash(String pack) {
				return m.get(pack);
			}

			@Override
			public void loaded(String pack, String hash) {
				m.put(pack, hash);
			}
		};
	}
}
