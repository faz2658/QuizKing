package com.fazeel.quizcadia.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fazeel.quizcadia.bank.NotFoundException;
import com.fazeel.quizcadia.bank.QuestionBankService;
import com.fazeel.quizcadia.domain.Questions;

/**
 * A topic's questions for the game engines, which load them when a game starts instead of keeping copies.
 * Reachable only inside the Docker network: the public nginx answers 404 for /internal/.
 */
@RestController
@RequestMapping("/internal/topics")
public class InternalTopicController {

	private final QuestionBankService bank;

	public InternalTopicController(QuestionBankService bank) {
		this.bank = bank;
	}

	/** The topic's playable questions; 404 for an unknown topic. */
	@GetMapping("/{topicId}/questions")
	public List<Questions> questions(@PathVariable int topicId) {
		return bank.topicQuestions(topicId);
	}

	@ExceptionHandler(NotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	void notFound() {
		// unknown topic: the engine treats the game as not ready
	}
}
