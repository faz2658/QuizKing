package com.fazeel.quizcadia.bank;

import static org.springframework.data.mongodb.core.FindAndModifyOptions.options;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.fazeel.quizcadia.domain.Category;
import com.fazeel.quizcadia.domain.Questions;
import com.fazeel.quizcadia.domain.Topic;
import com.fazeel.quizcadia.generator.FactQuestionGenerator;
import com.fazeel.quizcadia.generator.FactSource;
import com.fazeel.quizcadia.generator.QuestionTemplate;

/**
 * Reads and changes the question bank. Every change goes out as a bank event, with only the changed topic, so the
 * game manager can update its games.
 */
@Service
public class QuestionBankService {

	private final CategoryRepository categories;
	private final MongoOperations mongo;
	private final BankEvents events;
	private final FactSource facts;
	private final FactQuestionGenerator generator;

	public QuestionBankService(CategoryRepository categories, MongoOperations mongo, BankEvents events,
			FactSource facts, FactQuestionGenerator generator) {
		this.categories = categories;
		this.mongo = mongo;
		this.events = events;
		this.facts = facts;
		this.generator = generator;
	}

	/** True once any question of the given type is in the bank. */
	public boolean hasQuestionsOfType(String type) {
		return categories.findAll().stream().flatMap(c -> topics(c).stream()).flatMap(t -> questions(t).stream())
				.anyMatch(q -> type.equalsIgnoreCase(q.getQuestionType()));
	}

	/** Where a question lives in the bank, for feedback on a question id from a game. */
	public record Located(int categoryId, String topicName, Questions question) {
	}

	/**
	 * Finds one question by its id. Feedback calls this on every rating or flag, so the index finds the category
	 * holding it and only that one question comes back, instead of reading the whole bank (every category with all its
	 * questions: megabytes per tap).
	 */
	public Optional<Located> locate(int questionId) {
		var at = Aggregation.newAggregation(
				Aggregation.match(where("topic.questions.questionId").is(questionId)),
				Aggregation.unwind("topic"),
				Aggregation.unwind("topic.questions"),
				Aggregation.match(where("topic.questions.questionId").is(questionId)),
				Aggregation.limit(1),
				Aggregation.project()
						.and("topic.topicName").as("topicName").and("topic.questions").as("question"));
		Document hit = mongo.aggregate(at, "category", Document.class).getUniqueMappedResult();
		if (hit == null || !(hit.get("question") instanceof Document question)) {
			return Optional.empty();
		}
		return Optional.of(new Located(((Number) hit.get("_id")).intValue(), hit.getString("topicName"),
				mongo.getConverter().read(Questions.class, question)));
	}

	/** Every category with its topics and questions. */
	public List<Category> categories() {
		return categories.findAll();
	}

	/** Question counts per topic, without loading the questions. */
	public List<TopicCounts.TopicCount> topicCounts() {
		return TopicCounts.read(mongo);
	}

	/** One category; NotFoundException if there is none. */
	public Category category(int categoryId) {
		return categories.findById(categoryId).orElseThrow(() -> new NotFoundException("No category " + categoryId));
	}

	/** Creates an empty category with the next category id; the name must be new (ignoring case). */
	public Category createCategory(String name, String image) {
		if (name == null || name.isBlank()) {
			throw new InvalidQuestionException("categoryName is required");
		}
		if (categories.findByCategoryNameIgnoreCase(name.trim()).isPresent()) {
			throw new InvalidQuestionException("A category named '" + name.trim() + "' already exists");
		}
		Category c = new Category();
		c.setCategoryId(next("categories"));
		c.setCategoryName(name.trim());
		c.setCategoryImage(image);
		c.setTopic(new ArrayList<>());
		return publish(categories.save(c));
	}

	/** Adds an empty topic with the next topic id; the name must be new in the category (ignoring case). */
	public Category addTopic(int categoryId, String topicName, String image) {
		if (topicName == null || topicName.isBlank()) {
			throw new InvalidQuestionException("topicName is required");
		}
		Category c = category(categoryId);
		if (topics(c).stream().anyMatch(t -> t.getTopicName().equalsIgnoreCase(topicName.trim()))) {
			throw new InvalidQuestionException("Topic '" + topicName.trim() + "' already exists");
		}
		Topic t = new Topic();
		t.setTopicId(next("topics"));
		t.setTopicName(topicName.trim());
		t.setTopicImage(image);
		t.setQuestions(new ArrayList<>());
		List<Topic> topics = new ArrayList<>(topics(c));
		topics.add(t);
		c.setTopic(topics);
		return publish(categories.save(c));
	}

	/** A topic's questions, found by category id and topic name (ignoring case). */
	public List<Questions> questions(int categoryId, String topicName) {
		return questions(topic(category(categoryId), topicName));
	}

	/** Validates the question and adds it to the topic with the next question id; its type defaults to manual. */
	public Questions addQuestion(int categoryId, String topicName, Questions q) {
		QuestionRules.validate(q);
		Category c = category(categoryId);
		Topic t = topic(c, topicName);
		q.setQuestionId(next("questions"));
		if (q.getQuestionType() == null || q.getQuestionType().isBlank()) {
			q.setQuestionType("manual");
		}
		List<Questions> qs = new ArrayList<>(questions(t));
		qs.add(q);
		t.setQuestions(qs);
		categories.save(c);
		publishTopic(c, t);
		return q;
	}

	/** Validates the question and replaces the one with that id, keeping its type when none is given. */
	public Questions updateQuestion(int categoryId, String topicName, int questionId, Questions q) {
		QuestionRules.validate(q);
		Category c = category(categoryId);
		Topic t = topic(c, topicName);
		List<Questions> qs = new ArrayList<>(questions(t));
		int index = indexOf(qs, questionId);
		q.setQuestionId(questionId);
		if (q.getQuestionType() == null) {
			q.setQuestionType(qs.get(index).getQuestionType());
		}
		qs.set(index, q);
		t.setQuestions(qs);
		categories.save(c);
		publishTopic(c, t);
		return q;
	}

	/** Removes the question and publishes its id as removed, so the game manager drops it too. */
	public void deleteQuestion(int categoryId, String topicName, int questionId) {
		Category c = category(categoryId);
		Topic t = topic(c, topicName);
		List<Questions> qs = new ArrayList<>(questions(t));
		qs.remove(indexOf(qs, questionId));
		t.setQuestions(qs);
		categories.save(c);
		// tell the game manager, so the question also leaves its pools and the games built from them
		Topic removal = new Topic(t.getTopicId(), t.getTopicName(), t.getTopicImage(), List.of());
		removal.setRemovedQuestionIds(List.of(questionId));
		publishTopic(c, removal);
	}

	/** Generates questions from Wikidata and adds (or refreshes) them in the topic. */
	public List<Questions> generate(int categoryId, String topicName, String templateKey, int count) {
		QuestionTemplate template;
		try {
			template = QuestionTemplate.byKey(templateKey);
		}
		catch (IllegalArgumentException e) {
			throw new InvalidQuestionException(e.getMessage());
		}
		if (count < 1 || count > 200) {
			throw new InvalidQuestionException("count must be between 1 and 200");
		}
		Category c = category(categoryId);
		Topic t = topic(c, topicName);
		List<Questions> generated = generator.generate(template, facts.facts(template), count);
		Map<Integer, Questions> byId = new LinkedHashMap<>();
		questions(t).forEach(q -> byId.put(q.getQuestionId(), q));
		generated.forEach(q -> byId.put(q.getQuestionId(), q));
		t.setQuestions(new ArrayList<>(byId.values()));
		categories.save(c);
		publishTopic(c, t);
		return generated;
	}

	/**
	 * Adds externally sourced questions to a topic, creating the category and topic when needed.
	 * Questions are matched by id, so importing again updates instead of duplicating. Invalid
	 * questions are skipped.
	 *
	 * @return how many questions were new
	 */
	public int importQuestions(String categoryName, String topicName, List<Questions> questions) {
		return importQuestions(categoryName, null, topicName, questions);
	}

	/** Like {@link #importQuestions(String, String, List)}, also tagging the category with a country (e.g. IN). */
	public int importQuestions(String categoryName, String countryCode, String topicName, List<Questions> questions) {
		Category c = categories.findByCategoryNameIgnoreCase(categoryName.trim())
				.orElseGet(() -> createCategory(categoryName, null));
		if (countryCode != null && !countryCode.equals(c.getCountryCode())) {
			c.setCountryCode(countryCode);
			c = categories.save(c);
		}
		if (topics(c).stream().noneMatch(t -> t.getTopicName().equalsIgnoreCase(topicName.trim()))) {
			c = addTopic(c.getCategoryId(), topicName, null);
		}
		Topic t = topic(c, topicName);
		Map<Integer, Questions> byId = new LinkedHashMap<>();
		questions(t).forEach(q -> byId.put(q.getQuestionId(), q));
		int added = 0;
		for (Questions q : questions) {
			try {
				QuestionRules.validate(q);
			}
			catch (InvalidQuestionException e) {
				continue;
			}
			if (byId.put(q.getQuestionId(), q) == null) {
				added++;
			}
		}
		t.setQuestions(new ArrayList<>(byId.values()));
		categories.save(c);
		publishTopic(c, t);
		return added;
	}

	private int next(String sequence) {
		Map<?, ?> seq = mongo.findAndModify(query(where("_id").is(sequence)), new Update().inc("value", 1),
				options().returnNew(true).upsert(true), Map.class, "sequences");
		return ((Number) Objects.requireNonNull(seq).get("value")).intValue();
	}

	/** A topic's playable questions, as the game engines load them when a game starts. */
	public List<Questions> topicQuestions(int topicId) {
		Category c = categories.findFirstByTopic_TopicId(topicId)
				.orElseThrow(() -> new NotFoundException("No topic " + topicId));
		return TopicPool.playable(topics(c).stream().filter(t -> t.getTopicId() == topicId).findFirst()
				.orElseThrow(() -> new NotFoundException("No topic " + topicId)));
	}

	/**
	 * A quiz across every topic for the live room quiz: {@code count} general questions of one level, spread over the
	 * topics (see {@link QuestionMix}).
	 */
	public List<Questions> mixedQuestions(int level, int count, RandomGenerator random) {
		Map<Integer, List<Questions>> byTopic = new HashMap<>();
		for (Category c : categories.findAll()) {
			for (Topic t : topics(c)) {
				byTopic.computeIfAbsent(t.getTopicId(), k -> new ArrayList<>()).addAll(questions(t));
			}
		}
		return QuestionMix.pick(byTopic, level, count, random);
	}

	private static List<Topic> topics(Category c) {
		return c.getTopic() == null ? List.of() : c.getTopic();
	}

	private static List<Questions> questions(Topic t) {
		return t.getQuestions() == null ? List.of() : t.getQuestions();
	}

	private static Topic topic(Category c, String name) {
		return topics(c).stream().filter(t -> t.getTopicName().equalsIgnoreCase(name.trim())).findFirst()
				.orElseThrow(() -> new NotFoundException("No topic '" + name + "' in category " + c.getCategoryId()));
	}

	private static int indexOf(List<Questions> qs, int questionId) {
		for (int i = 0; i < qs.size(); i++) {
			if (qs.get(i).getQuestionId() == questionId) {
				return i;
			}
		}
		throw new NotFoundException("No question " + questionId);
	}

	private Category publish(Category c) {
		events.categoryChanged(c);
		return c;
	}

	/** Publishes just the changed topic; the game manager merges topics by name. */
	private void publishTopic(Category c, Topic t) {
		Category partial = new Category();
		partial.setCategoryId(c.getCategoryId());
		partial.setCategoryName(c.getCategoryName());
		partial.setCategoryImage(c.getCategoryImage());
		partial.setCountryCode(c.getCountryCode());
		partial.setTopic(List.of(t));
		events.categoryChanged(partial);
	}
}
