package com.fazeel.quizcadia.api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fazeel.quizcadia.auth.Callers;
import com.fazeel.quizcadia.knowme.KnowMeAttempt;
import com.fazeel.quizcadia.knowme.KnowMeQuiz;
import com.fazeel.quizcadia.knowme.KnowMeService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** "How well do you know me?" quizzes. Right answers are only shown to someone who has answered (or the owner). */
@RestController
@RequestMapping("/api/v1/knowme")
public class KnowMeController {

	private final KnowMeService service;

	public KnowMeController(KnowMeService service) {
		this.service = service;
	}

	/** A question the owner writes: 2 to 4 options and the index of the right one. */
	public record QuestionInput(String text, List<String> options, int answer) {
	}

	/** The owner's questions (the service accepts 3 to 15). */
	public record CreateRequest(@NotNull @Size(max = 15) List<QuestionInput> questions) {
	}

	/** A question as a friend sees it: {@code answer} is null until they've answered. */
	public record QuestionView(String text, List<String> options, Integer answer) {
	}

	/** A leaderboard row; you marks the player asking. */
	public record Entry(int rank, String name, int score, boolean you) {
	}

	/** {@code yourAnswers}/{@code yourScore} are set once you've answered; {@code own} is true for the quiz's owner. */
	public record QuizView(String id, String ownerName, boolean own, List<QuestionView> questions, List<Integer> yourAnswers,
			Integer yourScore, long players, List<Entry> leaderboard, Instant createdAt) {
	}

	/** One of the caller's own quizzes, with how many friends answered it. */
	public record MineEntry(String id, int questions, long players, Instant createdAt) {
	}

	/** The chosen option index for each question. */
	public record AnswerRequest(@NotNull @Size(max = 15) List<Integer> answers) {
	}

	/** Creates a quiz about the caller. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public QuizView create(@Valid @RequestBody CreateRequest r) {
		int me = Callers.currentUserId();
		KnowMeQuiz quiz = service.create(me, Callers.currentUserName(),
				r.questions().stream().map(q -> new KnowMeQuiz.Question(q.text(), q.options(), q.answer())).toList());
		return view(quiz, me);
	}

	/** The caller's own quizzes, newest first. */
	@GetMapping("/mine")
	public List<MineEntry> mine() {
		return service.mine(Callers.currentUserId()).stream()
				.map(q -> new MineEntry(q.id(), q.questions().size(), service.players(q.id()), q.createdAt())).toList();
	}

	/** The quiz as the caller sees it. */
	@GetMapping("/{id}")
	public QuizView get(@PathVariable String id) {
		return view(service.get(id), Callers.currentUserId());
	}

	/** Scores the caller's one go and returns the quiz with the right answers. */
	@PostMapping("/{id}/answers")
	public QuizView answer(@PathVariable String id, @Valid @RequestBody AnswerRequest r) {
		int me = Callers.currentUserId();
		service.answer(id, me, Callers.currentUserName(), r.answers());
		return view(service.get(id), me);
	}

	/** Deletes one of the caller's quizzes and every answer to it. */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable String id) {
		service.delete(id, Callers.currentUserId());
	}

	private QuizView view(KnowMeQuiz quiz, int me) {
		boolean own = quiz.ownerId() == me;
		Optional<KnowMeAttempt> mine = service.attempt(quiz.id(), me);
		boolean reveal = own || mine.isPresent();
		List<QuestionView> questions = quiz.questions().stream()
				.map(q -> new QuestionView(q.text(), q.options(), reveal ? q.answer() : null)).toList();
		List<KnowMeAttempt> board = service.leaderboard(quiz.id());
		List<Entry> top = new ArrayList<>();
		int rank = 0;
		int lastScore = -1;
		for (int i = 0; i < board.size(); i++) {
			KnowMeAttempt a = board.get(i);
			if (a.score() != lastScore) {
				rank = i + 1;
				lastScore = a.score();
			}
			if (i < 50 || a.userId() == me) {
				top.add(new Entry(rank, a.name(), a.score(), a.userId() == me));
			}
		}
		return new QuizView(quiz.id(), quiz.ownerName(), own, questions, mine.map(KnowMeAttempt::answers).orElse(null),
				mine.map(KnowMeAttempt::score).orElse(null), board.size(), top, quiz.createdAt());
	}
}
