package com.fazeel.quizcadia.bank;

import static org.springframework.data.mongodb.core.FindAndModifyOptions.options;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.fazeel.quizcadia.domain.Category;
import com.fazeel.quizcadia.domain.Questions;
import com.fazeel.quizcadia.domain.Topic;
import com.fazeel.quizcadia.generator.FactQuestionGenerator;
import com.fazeel.quizcadia.generator.FactSource;
import com.fazeel.quizcadia.generator.QuestionTemplate;

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

	public java.util.Optional<Located> locate(int questionId) {
		for (Category c : categories.findAll()) {
			for (Topic t : c.getTopic() == null ? List.<Topic>of() : c.getTopic()) {
				for (Questions q : t.getQuestions() == null ? List.<Questions>of() : t.getQuestions()) {
					if (q.getQuestionId() == questionId) {
						return java.util.Optional.of(new Located(c.getCategoryId(), t.getTopicName(), q));
					}
				}
			}
		}
		return java.util.Optional.empty();
	}

	public List<Category> categories() {
		return categories.findAll();
	}

	public Category category(int categoryId) {
		return categories.findById(categoryId).orElseThrow(() -> new NotFoundException("No category " + categoryId));
	}

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

	public List<Questions> questions(int categoryId, String topicName) {
		return questions(topic(category(categoryId), topicName));
	}

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
		Map<Integer, Questions> byId = new java.util.LinkedHashMap<>();
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
		Category c = categories.findByCategoryNameIgnoreCase(categoryName.trim())
				.orElseGet(() -> createCategory(categoryName, null));
		if (topics(c).stream().noneMatch(t -> t.getTopicName().equalsIgnoreCase(topicName.trim()))) {
			c = addTopic(c.getCategoryId(), topicName, null);
		}
		Topic t = topic(c, topicName);
		Map<Integer, Questions> byId = new java.util.LinkedHashMap<>();
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
		partial.setTopic(List.of(t));
		events.categoryChanged(partial);
	}
}
