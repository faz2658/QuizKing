package com.fazeel.quizcadia.topicquiz;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.fazeel.quizcadia.ai.AiUnavailableException;
import com.fazeel.quizcadia.ai.GeneratedQuestion;
import com.fazeel.quizcadia.ai.QuestionModel;
import com.fazeel.quizcadia.topicquiz.TopicQuiz.Question;
import com.fazeel.quizcadia.topicquiz.TopicQuiz.Status;

/**
 * "AI quiz on any topic": a player types a topic, the fine-tuned model writes {@link #QUESTIONS} questions in the
 * background (one model call each, a few seconds apiece on the server's CPU), and the quiz is shared as a link.
 * Every player gets one go and a place on the quiz's leaderboard.
 */
@Service
public class TopicQuizService {

	private static final Logger log = LoggerFactory.getLogger(TopicQuizService.class);

	static final int QUESTIONS = 8;
	/** Ready with fewer if the model struggles with a topic, but not with fewer than this. */
	static final int MIN_QUESTIONS = 5;
	static final int ATTEMPTS = QUESTIONS * 3;
	static final int PER_PLAYER_PER_DAY = 5;
	/** Quizzes waiting for or being written at once; more and players are asked to come back shortly. */
	static final int MAX_IN_QUEUE = 6;
	private static final String[] LEVELS = { "easy", "easy", "medium", "medium", "medium", "hard", "hard", "hard" };
	/** Not a moderation system: only keeps the most obvious abuse out of shareable links. */
	private static final Pattern BLOCKED = Pattern.compile(
			"\\b(porn\\w*|sex\\w*|nude\\w*|nsfw|rape\\w*|nazi\\w*|hitler|terroris\\w*|suicide|self[- ]harm|genocide)\\b");
	private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";

	private final SecureRandom ids = new SecureRandom();
	private final TopicQuizRepository quizzes;
	private final TopicQuizAttemptRepository attempts;
	private final QuestionModel model;
	private final Executor executor;
	private final Clock clock;

	public TopicQuizService(TopicQuizRepository quizzes, TopicQuizAttemptRepository attempts, QuestionModel model,
			@Qualifier("topicQuizExecutor") Executor executor, Clock clock) {
		this.quizzes = quizzes;
		this.attempts = attempts;
		this.model = model;
		this.executor = executor;
		this.clock = clock;
	}

	public boolean available() {
		return model.available();
	}

	public TopicQuiz create(int userId, String name, String topic) {
		if (!model.available()) {
			throw new AiUnavailableException("AI quizzes are switched off on this server");
		}
		String t = topic == null ? "" : topic.trim().replaceAll("\\s+", " ");
		if (t.length() < 2 || t.length() > 60) {
			throw new TopicQuizException("Pick a topic of 2 to 60 characters");
		}
		if (BLOCKED.matcher(t.toLowerCase(Locale.ROOT)).find()) {
			throw new TopicQuizException("Let's keep it fun: pick another topic");
		}
		if (quizzes.countByCreatorIdAndCreatedAtAfter(userId, clock.instant().minus(Duration.ofDays(1))) >= PER_PLAYER_PER_DAY) {
			throw new TopicQuizException("You've made " + PER_PLAYER_PER_DAY + " AI quizzes today. Come back tomorrow for more!");
		}
		if (quizzes.countByStatus(Status.GENERATING) >= MAX_IN_QUEUE) {
			throw TopicQuizException.busy("The AI is busy writing other quizzes. Try again in a minute.");
		}
		TopicQuiz quiz = quizzes.save(new TopicQuiz(newId(), t, userId, name, QUESTIONS, clock.instant()));
		executor.execute(() -> generate(quiz.getId()));
		return quiz;
	}

	/** Quizzes a restart interrupted are picked up again. */
	@EventListener(ApplicationReadyEvent.class)
	public void resumeUnfinished() {
		quizzes.findByStatus(Status.GENERATING).forEach(q -> executor.execute(() -> generate(q.getId())));
	}

	void generate(String id) {
		TopicQuiz quiz = quizzes.findById(id).orElse(null);
		if (quiz == null || quiz.getStatus() != Status.GENERATING) {
			return;
		}
		Set<String> seen = new HashSet<>();
		quiz.getQuestions().forEach(q -> seen.add(key(q.text())));
		Random shuffle = new Random(id.hashCode());
		try {
			for (int attempt = 0; attempt < ATTEMPTS && quiz.getQuestions().size() < quiz.getTarget(); attempt++) {
				String level = LEVELS[Math.min(quiz.getQuestions().size(), LEVELS.length - 1)];
				// mostly multiple choice, with the odd true/false for variety
				String format = quiz.getQuestions().size() % 4 == 3 ? "true/false" : "multiple choice";
				Optional<Question> q = model.write(quiz.getTopic(), level, format).flatMap(g -> clean(g, format, shuffle));
				if (q.isPresent() && seen.add(key(q.get().text()))) {
					quiz.add(q.get());
					quiz = quizzes.save(quiz); // players watching see the progress
				}
			}
			if (quiz.getQuestions().size() >= MIN_QUESTIONS) {
				quiz.ready();
			} else {
				quiz.failed("The AI couldn't write enough good questions about this topic. Try a broader one.");
			}
		}
		catch (AiUnavailableException e) {
			log.warn("Topic quiz {} stopped: {}", id, e.getMessage());
			quiz.failed("The AI is offline right now. Try again in a few minutes.");
		}
		catch (RuntimeException e) {
			log.error("Topic quiz {} failed", id, e);
			quiz.failed("Something went wrong writing this quiz. Try again.");
		}
		quizzes.save(quiz);
	}

	/** A usable question, with the options shuffled (the model tends to put the answer in the same place). */
	static Optional<Question> clean(GeneratedQuestion g, String format, Random shuffle) {
		String text = g.question() == null ? "" : g.question().trim();
		List<String> options = g.options() == null ? List.of()
				: g.options().stream().map(o -> o == null ? "" : o.trim()).toList();
		String answer = g.answer() == null ? "" : g.answer().trim();
		int expected = format.equals("true/false") ? 2 : 4;
		if (text.length() < 8 || text.length() > 300 || options.size() != expected) {
			return Optional.empty();
		}
		Set<String> distinct = new HashSet<>();
		for (String o : options) {
			if (o.isEmpty() || o.length() > 120 || !distinct.add(o.toLowerCase(Locale.ROOT))) {
				return Optional.empty();
			}
		}
		if (options.stream().noneMatch(o -> o.equalsIgnoreCase(answer))) {
			return Optional.empty();
		}
		List<String> shuffled = new ArrayList<>(options);
		if (expected == 4) {
			Collections.shuffle(shuffled, shuffle);
		}
		int right = 0;
		while (!shuffled.get(right).equalsIgnoreCase(answer)) {
			right++;
		}
		return Optional.of(new Question(text, List.copyOf(shuffled), right));
	}

	public TopicQuiz get(String id) {
		return quizzes.findById(id).orElseThrow(() -> TopicQuizException.notFound("This quiz doesn't exist (any more)"));
	}

	public Optional<TopicQuizAttempt> attempt(String quizId, int userId) {
		return attempts.findById(TopicQuizAttempt.idOf(quizId, userId));
	}

	public List<TopicQuizAttempt> leaderboard(String quizId) {
		return attempts.findByQuizIdOrderByScoreDescCompletedAtAsc(quizId);
	}

	/** Scores a player's one go; -1 is a question the timer ran out on. */
	public TopicQuizAttempt answer(String quizId, int userId, String name, List<Integer> answers) {
		TopicQuiz quiz = get(quizId);
		if (quiz.getStatus() != Status.READY) {
			throw new TopicQuizException("This quiz isn't ready yet");
		}
		if (attempt(quizId, userId).isPresent()) {
			throw new TopicQuizException("You've already played this quiz");
		}
		List<Question> qs = quiz.getQuestions();
		if (answers == null || answers.size() != qs.size()) {
			throw new TopicQuizException("Send an answer for every question");
		}
		int score = 0;
		for (int i = 0; i < qs.size(); i++) {
			Integer a = answers.get(i);
			if (a == null || a < -1 || a >= qs.get(i).options().size()) {
				throw new TopicQuizException("Answer " + (i + 1) + " isn't one of the options");
			}
			if (a == qs.get(i).answer()) {
				score++;
			}
		}
		try {
			return attempts.insert(new TopicQuizAttempt(TopicQuizAttempt.idOf(quizId, userId), quizId, userId, name,
					List.copyOf(answers), score, clock.instant()));
		}
		catch (DuplicateKeyException e) {
			throw new TopicQuizException("You've already played this quiz");
		}
	}

	private static String key(String text) {
		return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

	private String newId() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 8; i++) {
			sb.append(ALPHABET.charAt(ids.nextInt(ALPHABET.length())));
		}
		return sb.toString();
	}
}
