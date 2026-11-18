package com.fazeel.quizcadia.api;

import java.security.SecureRandom;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fazeel.quizcadia.bank.QuestionBankService;
import com.fazeel.quizcadia.bank.QuestionMix;
import com.fazeel.quizcadia.domain.Questions;

/**
 * Questions from every topic at once, for the multiplayer engine's live room quiz. Like the topic pools, only for
 * the engines inside the Docker network: the public nginx answers 404 for /internal/.
 */
@RestController
@RequestMapping("/internal/questions")
public class InternalQuestionController {

	private final QuestionBankService bank;
	private final SecureRandom random = new SecureRandom();

	public InternalQuestionController(QuestionBankService bank) {
		this.bank = bank;
	}

	static final int MAX_COUNT = 50;

	/**
	 * {@code count} random general questions of {@code level} (1 easy, 2 medium, 3 advanced), no repeats. Out-of-range
	 * values are clamped (at most {@value #MAX_COUNT} questions).
	 */
	@GetMapping("/mix")
	public List<Questions> mix(@RequestParam(defaultValue = "" + QuestionMix.MEDIUM) int level,
			@RequestParam(defaultValue = "20") int count) {
		return bank.mixedQuestions(Math.clamp(level, QuestionMix.EASY, QuestionMix.ADVANCED), Math.clamp(count, 1, MAX_COUNT),
				random);
	}
}
