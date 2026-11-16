package com.fazeel.quizcadia.feedback;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.fazeel.quizcadia.bank.NotFoundException;
import com.fazeel.quizcadia.bank.QuestionBankService;
import com.fazeel.quizcadia.bank.QuestionBankService.Located;
import com.fazeel.quizcadia.domain.Questions;
import com.fazeel.quizcadia.feedback.QuestionFeedback.FlagReason;

/**
 * Player ratings and flags. A question is retired (taken out of the bank and every game, kept for an admin to
 * restore) when signed-in players clearly dislike it: at least {@value #MIN_VOTES} account ratings with
 * {@value #DOWN_SHARE}% or more thumbs down, or {@value #FLAGS_TO_RETIRE} account flags.
 */
@Service
public class FeedbackService {

	static final int MIN_VOTES = 5;
	static final int DOWN_SHARE = 80;
	static final int FLAGS_TO_RETIRE = 3;
	static final int MAX_NOTE = 200;

	private final FeedbackRepository feedback;
	private final RetiredQuestionRepository retired;
	private final QuestionBankService bank;
	private final Clock clock;

	public FeedbackService(FeedbackRepository feedback, RetiredQuestionRepository retired, QuestionBankService bank,
			Clock clock) {
		this.feedback = feedback;
		this.retired = retired;
		this.bank = bank;
		this.clock = clock;
	}

	/** Records a player's rating and/or flag; returns true if the question got retired by it. */
	public boolean give(int userId, boolean account, int questionId, int rating, FlagReason flag, String note) {
		if (rating < -1 || rating > 1) {
			throw new IllegalArgumentException("rating must be -1, 0 or 1");
		}
		Located where = bank.locate(questionId).orElseThrow(() -> new NotFoundException("No question " + questionId));
		String cleanNote = note == null || note.isBlank() ? null : note.trim().substring(0, Math.min(MAX_NOTE, note.trim().length()));
		Optional<QuestionFeedback> before = feedback.findById(QuestionFeedback.id(userId, questionId));
		boolean flagged = flag != null || before.map(QuestionFeedback::flagged).orElse(false);
		FlagReason reason = flag != null ? flag : before.map(QuestionFeedback::reason).orElse(null);
		feedback.save(new QuestionFeedback(QuestionFeedback.id(userId, questionId), questionId, userId, account, rating,
				flagged, reason, cleanNote != null ? cleanNote : before.map(QuestionFeedback::note).orElse(null),
				clock.instant()));
		return account && retireIfDisliked(where);
	}

	private boolean retireIfDisliked(Located where) {
		Tally t = Tally.of(feedback.findByQuestionId(where.question().getQuestionId()), true);
		String why = null;
		if (t.flags() >= FLAGS_TO_RETIRE) {
			why = t.flags() + " players flagged it";
		} else if (t.ups() + t.downs() >= MIN_VOTES && 100 * t.downs() >= DOWN_SHARE * (t.ups() + t.downs())) {
			why = t.downs() + " of " + (t.ups() + t.downs()) + " ratings were thumbs down";
		}
		if (why == null) {
			return false;
		}
		Tally all = Tally.of(feedback.findByQuestionId(where.question().getQuestionId()), false);
		retired.save(new RetiredQuestion(where.question().getQuestionId(), where.categoryId(), where.topicName(),
				where.question(), why, all.ups(), all.downs(), all.flags(), clock.instant()));
		bank.deleteQuestion(where.categoryId(), where.topicName(), where.question().getQuestionId());
		return true;
	}

	/** Questions players complained about or rated, worst first, plus the ones already retired. */
	public Review review() {
		Map<Integer, List<QuestionFeedback>> byQuestion = feedback.findAll().stream()
				.collect(Collectors.groupingBy(QuestionFeedback::questionId));
		List<ReviewItem> items = new ArrayList<>();
		byQuestion.forEach((questionId, list) -> bank.locate(questionId).ifPresent(where -> {
			Tally t = Tally.of(list, false);
			if (t.flags() == 0 && t.downs() == 0) {
				return; // only praise: nothing to review
			}
			Map<FlagReason, Long> reasons = new EnumMap<>(FlagReason.class);
			list.stream().filter(QuestionFeedback::flagged).filter(f -> f.reason() != null)
					.forEach(f -> reasons.merge(f.reason(), 1L, Long::sum));
			List<String> notes = list.stream().filter(f -> f.note() != null)
					.sorted(Comparator.comparing(QuestionFeedback::updatedAt).reversed()).map(QuestionFeedback::note)
					.limit(5).toList();
			items.add(new ReviewItem(questionId, where.categoryId(), where.topicName(), where.question(), t.ups(),
					t.downs(), t.flags(), reasons, notes));
		}));
		items.sort(Comparator.comparingInt(ReviewItem::flags).reversed()
				.thenComparing(Comparator.comparingInt((ReviewItem i) -> i.downs() - i.ups()).reversed()));
		List<RetiredQuestion> gone = new ArrayList<>(retired.findAll());
		gone.sort(Comparator.comparing(RetiredQuestion::retiredAt).reversed());
		return new Review(items, gone);
	}

	/** The admin looked and the question is fine (or fixed): its feedback starts over. */
	public void dismiss(int questionId) {
		feedback.deleteByQuestionId(questionId);
	}

	/** Puts a retired question back into its topic (it gets a new id) and clears its feedback. */
	public Questions restore(int questionId) {
		RetiredQuestion r = retired.findById(questionId).orElseThrow(() -> new NotFoundException("No retired question " + questionId));
		Questions copy = r.question();
		Questions restored = bank.addQuestion(r.categoryId(), r.topicName(), copy);
		retired.deleteById(questionId);
		feedback.deleteByQuestionId(questionId);
		return restored;
	}

	record Tally(int ups, int downs, int flags) {
		static Tally of(List<QuestionFeedback> list, boolean accountsOnly) {
			int ups = 0;
			int downs = 0;
			int flags = 0;
			for (QuestionFeedback f : list) {
				if (accountsOnly && !f.account()) {
					continue;
				}
				ups += f.rating() > 0 ? 1 : 0;
				downs += f.rating() < 0 ? 1 : 0;
				flags += f.flagged() ? 1 : 0;
			}
			return new Tally(ups, downs, flags);
		}
	}

	public record ReviewItem(int questionId, int categoryId, String topicName, Questions question, int ups, int downs,
			int flags, Map<FlagReason, Long> reasons, List<String> notes) {
	}

	public record Review(List<ReviewItem> questions, List<RetiredQuestion> retired) {
	}
}
