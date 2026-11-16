package com.fazeel.quizcadia.ai;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fazeel.quizcadia.bank.InvalidQuestionException;
import com.fazeel.quizcadia.bank.NotFoundException;
import com.fazeel.quizcadia.bank.QuestionBankService;
import com.fazeel.quizcadia.bank.QuestionRules;
import com.fazeel.quizcadia.domain.Questions;

/**
 * AI question drafts: generate with the question model, check, and queue for review. A draft becomes
 * a real question (type "ai") only when an admin approves it.
 */
@Service
public class DraftService {

	static final int MAX_PER_REQUEST = 20;
	/** Tries per requested question before giving up (invalid or duplicate replies). */
	static final int ATTEMPTS_PER_QUESTION = 3;
	private static final String[] LEVELS = { "easy", "medium", "hard" };

	private final QuestionModel model;
	private final DraftRepository drafts;
	private final QuestionBankService bank;
	private final Clock clock;

	public DraftService(QuestionModel model, DraftRepository drafts, QuestionBankService bank, Clock clock) {
		this.model = model;
		this.drafts = drafts;
		this.bank = bank;
		this.clock = clock;
	}

	public boolean available() {
		return model.available();
	}

	public GenerationResult generate(int categoryId, String topicName, int level, boolean trueFalse, int count) {
		if (!model.available()) {
			throw new AiUnavailableException("No question model is configured (set AI_MODEL_URL)");
		}
		if (count < 1 || count > MAX_PER_REQUEST) {
			throw new InvalidQuestionException("count must be between 1 and " + MAX_PER_REQUEST);
		}
		if (level < 1 || level > 3) {
			throw new InvalidQuestionException("level must be 1 (easy), 2 (medium) or 3 (hard)");
		}
		List<Questions> existing = bank.questions(categoryId, topicName); // also 404s on unknown topics
		String category = bank.category(categoryId).getCategoryName();
		String topicLabel = category.equalsIgnoreCase(topicName) ? topicName : category + ": " + topicName;

		Set<String> known = new HashSet<>();
		existing.forEach(q -> known.add(key(q.getQuestionStem())));
		drafts.findByCategoryIdAndTopicNameIgnoreCase(categoryId, topicName)
				.forEach(d -> known.add(key(d.question().getQuestionStem())));

		int created = 0;
		int invalid = 0;
		int duplicates = 0;
		List<DraftQuestion> out = new java.util.ArrayList<>();
		for (int attempt = 0; attempt < count * ATTEMPTS_PER_QUESTION && created < count; attempt++) {
			var reply = model.write(topicLabel, LEVELS[level - 1], trueFalse ? "true/false" : "multiple choice");
			if (reply.isEmpty()) {
				invalid++;
				continue;
			}
			int optionCount = reply.get().options() == null ? 0 : reply.get().options().size();
			if (optionCount != (trueFalse ? 2 : 4)) {
				invalid++; // not the format that was asked for
				continue;
			}
			Questions q = toQuestion(reply.get(), level);
			try {
				QuestionRules.validate(q);
			}
			catch (InvalidQuestionException e) {
				invalid++;
				continue;
			}
			if (!known.add(key(q.getQuestionStem()))) {
				duplicates++;
				continue;
			}
			DraftQuestion d = drafts.save(new DraftQuestion(UUID.randomUUID().toString(), categoryId, topicName,
					DraftQuestion.Status.PENDING, q, clock.instant(), null));
			out.add(d);
			created++;
		}
		return new GenerationResult(count, created, invalid, duplicates, out);
	}

	public List<DraftQuestion> pending() {
		return drafts.findByStatusOrderByCreatedAtDesc(DraftQuestion.Status.PENDING);
	}

	/** Adds the draft (optionally edited) to its topic as a real question. */
	public Questions approve(String draftId, Questions edited) {
		DraftQuestion d = pendingDraft(draftId);
		Questions q = edited == null ? d.question() : edited;
		q.setQuestionType("ai");
		if (q.getQuestionLevel() == 0) {
			q.setQuestionLevel(d.question().getQuestionLevel());
		}
		Questions added = bank.addQuestion(d.categoryId(), d.topicName(), q);
		drafts.save(d.reviewed(DraftQuestion.Status.APPROVED, clock.instant(), added));
		return added;
	}

	public void reject(String draftId) {
		DraftQuestion d = pendingDraft(draftId);
		drafts.save(d.reviewed(DraftQuestion.Status.REJECTED, clock.instant(), d.question()));
	}

	private DraftQuestion pendingDraft(String id) {
		DraftQuestion d = drafts.findById(id).orElseThrow(() -> new NotFoundException("No draft " + id));
		if (d.status() != DraftQuestion.Status.PENDING) {
			throw new InvalidQuestionException("This draft was already " + d.status().name().toLowerCase(Locale.ROOT));
		}
		return d;
	}

	static Questions toQuestion(GeneratedQuestion g, int level) {
		List<String> opts = g.options() == null ? List.of() : g.options();
		Questions q = new Questions();
		q.setQuestionStem(g.question() == null ? null : g.question().trim());
		q.setQuestionLevel(level);
		q.setQuestionType("ai");
		q.setOption1(opts.size() > 0 ? opts.get(0) : null);
		q.setOption2(opts.size() > 1 ? opts.get(1) : null);
		q.setOption3(opts.size() > 2 ? opts.get(2) : null);
		q.setOption4(opts.size() > 3 ? opts.get(3) : null);
		q.setCorrectAnswer(g.answer());
		if (opts.size() != 2 && opts.size() != 4) {
			q.setOption1(null); // makes validation reject it: we only accept true/false or 4 options
			q.setOption2(null);
		}
		return q;
	}

	/** Case- and punctuation-insensitive form of a question, for duplicate checks. */
	static String key(String stem) {
		return stem == null ? "" : stem.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
	}

	public record GenerationResult(int requested, int created, int invalidReplies, int duplicates,
			List<DraftQuestion> drafts) {
	}
}
