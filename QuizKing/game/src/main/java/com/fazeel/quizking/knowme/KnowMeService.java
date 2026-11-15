package com.fazeel.quizking.knowme;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.fazeel.quizking.knowme.KnowMeQuiz.Question;
import com.fazeel.quizking.service.NotFoundException;
import com.fazeel.quizking.session.QuizRuleException;

/**
 * "How well do you know me?" quizzes. The owner writes 3 to 15 questions about themselves; every friend gets one
 * go, sees which answers were right afterwards, and lands on the quiz's leaderboard.
 */
@Service
public class KnowMeService {

	static final int MIN_QUESTIONS = 3;
	static final int MAX_QUESTIONS = 15;
	static final int MAX_QUIZZES_PER_PLAYER = 20;

	private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
	private final SecureRandom random = new SecureRandom();

	private final KnowMeQuizRepository quizzes;
	private final KnowMeAttemptRepository attempts;
	private final Clock clock;

	public KnowMeService(KnowMeQuizRepository quizzes, KnowMeAttemptRepository attempts, Clock clock) {
		this.quizzes = quizzes;
		this.attempts = attempts;
		this.clock = clock;
	}

	public KnowMeQuiz create(int ownerId, String ownerName, List<Question> questions) {
		if (questions == null || questions.size() < MIN_QUESTIONS || questions.size() > MAX_QUESTIONS) {
			throw new QuizRuleException("Write between " + MIN_QUESTIONS + " and " + MAX_QUESTIONS + " questions");
		}
		if (quizzes.countByOwnerId(ownerId) >= MAX_QUIZZES_PER_PLAYER) {
			throw new QuizRuleException("You have " + MAX_QUIZZES_PER_PLAYER + " quizzes already: that's plenty of you!");
		}
		List<Question> clean = questions.stream().map(KnowMeService::clean).toList();
		return quizzes.save(new KnowMeQuiz(newId(), ownerId, ownerName, clean, clock.instant()));
	}

	public KnowMeQuiz get(String id) {
		return quizzes.findById(id).orElseThrow(() -> new NotFoundException("This quiz doesn't exist (any more)"));
	}

	public List<KnowMeQuiz> mine(int ownerId) {
		return quizzes.findByOwnerIdOrderByCreatedAtDesc(ownerId);
	}

	public Optional<KnowMeAttempt> attempt(String quizId, int userId) {
		return attempts.findById(KnowMeAttempt.idOf(quizId, userId));
	}

	public List<KnowMeAttempt> leaderboard(String quizId) {
		return attempts.findByQuizIdOrderByScoreDescCompletedAtAsc(quizId);
	}

	public long players(String quizId) {
		return attempts.countByQuizId(quizId);
	}

	/** Scores a friend's answers (one go each; the owner can't answer their own quiz). */
	public KnowMeAttempt answer(String quizId, int userId, String name, List<Integer> answers) {
		KnowMeQuiz quiz = get(quizId);
		if (quiz.ownerId() == userId) {
			throw new QuizRuleException("You know yourself already: send the link to your friends");
		}
		if (attempt(quizId, userId).isPresent()) {
			throw new QuizRuleException("You've already answered this quiz");
		}
		if (answers == null || answers.size() != quiz.questions().size()) {
			throw new QuizRuleException("Answer every question");
		}
		int score = 0;
		for (int i = 0; i < answers.size(); i++) {
			Integer a = answers.get(i);
			Question q = quiz.questions().get(i);
			if (a == null || a < 0 || a >= q.options().size()) {
				throw new QuizRuleException("Pick one of the options for question " + (i + 1));
			}
			if (a == q.answer()) {
				score++;
			}
		}
		try {
			return attempts.insert(new KnowMeAttempt(KnowMeAttempt.idOf(quizId, userId), quizId, userId, name,
					List.copyOf(answers), score, clock.instant()));
		}
		catch (DuplicateKeyException e) { // the same answers sent twice at once
			throw new QuizRuleException("You've already answered this quiz");
		}
	}

	/** Deletes one of the owner's quizzes and everyone's answers to it. */
	public void delete(String quizId, int userId) {
		KnowMeQuiz quiz = get(quizId);
		if (quiz.ownerId() != userId) {
			throw new QuizRuleException("Only the person who made this quiz can delete it");
		}
		attempts.deleteAll(leaderboard(quizId));
		quizzes.delete(quiz);
	}

	private static Question clean(Question q) {
		String text = q.text() == null ? "" : q.text().trim();
		if (text.isEmpty() || text.length() > 140) {
			throw new QuizRuleException("Each question needs 1 to 140 characters");
		}
		if (q.options() == null || q.options().size() < 2 || q.options().size() > 4) {
			throw new QuizRuleException("Give \"" + text + "\" 2 to 4 options");
		}
		List<String> options = q.options().stream().map(o -> o == null ? "" : o.trim()).toList();
		Set<String> seen = new HashSet<>();
		for (String o : options) {
			if (o.isEmpty() || o.length() > 80) {
				throw new QuizRuleException("Options need 1 to 80 characters (\"" + text + "\")");
			}
			if (!seen.add(o.toLowerCase(Locale.ROOT))) {
				throw new QuizRuleException("\"" + text + "\" has the same option twice");
			}
		}
		if (q.answer() < 0 || q.answer() >= options.size()) {
			throw new QuizRuleException("Mark the right answer for \"" + text + "\"");
		}
		return new Question(text, options, q.answer());
	}

	private String newId() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 8; i++) {
			sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
		}
		return sb.toString();
	}
}
