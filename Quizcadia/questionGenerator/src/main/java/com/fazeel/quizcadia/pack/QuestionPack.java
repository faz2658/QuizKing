package com.fazeel.quizcadia.pack;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.json.JsonMapper;

import com.fazeel.quizcadia.domain.Questions;

/**
 * A hand-written set of questions bundled with the app (resources/packs/*.json), such as the India &amp;
 * South Asia pack. Each topic becomes a topic in the bank, so the game manager gives it a solo and a
 * multiplayer game automatically.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QuestionPack(String category, String country, List<PackTopic> topics) {

	/** Pack ids live in [1.3e9, 1.5e9): below imported ones, above generated ones. */
	static final int PACK_ID_BASE = 1_300_000_000;
	static final int PACK_ID_SPAN = 200_000_000;
	public static final String TYPE = "pack";

	/**
	 * @param category the shared bank category the topic belongs to (e.g. History); when set, the topic's
	 *        questions join that shared topic, each tagged with the pack's country, instead of a country topic
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record PackTopic(String category, String name, List<PackQuestion> questions) {

		public PackTopic(String name, List<PackQuestion> questions) {
			this(null, name, questions);
		}

		/** True when the topic belongs to a shared category rather than its own country topic. */
		public boolean shared() {
			return category != null && !category.isBlank();
		}
	}

	/** {@code a} is the answer, {@code wrong} the other options; {@code why} is shown after answering. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record PackQuestion(String q, String a, List<String> wrong, int level, String why) {
	}

	/** Reads a pack from the classpath. */
	public static QuestionPack load(String resource) {
		try (InputStream in = new ClassPathResource(resource).getInputStream()) {
			return parse(in.readAllBytes());
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Reads a pack from its JSON. */
	public static QuestionPack parse(byte[] json) {
		return JsonMapper.builder().build().readValue(json, QuestionPack.class);
	}

	/** The bank questions of one topic. Options are shuffled the same way every time, so ids and order stay stable. */
	public static List<Questions> questions(PackTopic topic) {
		return questions(topic, null);
	}

	/** Like {@link #questions(PackTopic)}, tagging each question with the pack's country (e.g. IN) when it has one. */
	public static List<Questions> questions(PackTopic topic, String country) {
		String tag = country == null || country.isBlank() ? null : country.trim().toUpperCase(Locale.ROOT);
		List<Questions> out = new ArrayList<>();
		for (PackQuestion p : topic.questions()) {
			Questions q = new Questions();
			int id = idFor(p.q());
			List<String> options = new ArrayList<>(p.wrong());
			options.add(p.a());
			Collections.shuffle(options, new Random(id));
			q.setQuestionId(id);
			q.setQuestionLevel(p.level());
			q.setQuestionType(TYPE);
			q.setQuestionStem(p.q().trim());
			q.setOption1(options.get(0));
			q.setOption2(options.get(1));
			q.setOption3(options.size() > 2 ? options.get(2) : null);
			q.setOption4(options.size() > 3 ? options.get(3) : null);
			q.setCorrectAnswer(p.a().trim());
			q.setExplanation(p.why());
			q.setCountry(tag);
			out.add(q);
		}
		return out;
	}

	/** Same question text always gets the same id, so loading the pack again updates instead of duplicating. */
	static int idFor(String questionText) {
		return PACK_ID_BASE + Math.floorMod(questionText.trim().toLowerCase(Locale.ROOT).hashCode(), PACK_ID_SPAN);
	}
}
