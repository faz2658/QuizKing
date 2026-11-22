package com.fazeel.quizcadia.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fazeel.quizcadia.auth.Callers;
import com.fazeel.quizcadia.bank.InvalidQuestionException;
import com.fazeel.quizcadia.bank.NotFoundException;
import com.fazeel.quizcadia.domain.Questions;
import com.fazeel.quizcadia.feedback.FeedbackService;
import com.fazeel.quizcadia.feedback.FeedbackService.Review;
import com.fazeel.quizcadia.feedback.QuestionFeedback.FlagReason;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Players rate and flag questions; admins review what they said. */
@RestController
@RequestMapping("/api/v1/question-generator/feedback")
public class FeedbackController {

	private final FeedbackService feedback;

	public FeedbackController(FeedbackService feedback) {
		this.feedback = feedback;
	}

	/** {@code rating} is -1, 0 or 1; {@code flag} (optional) reports a problem. */
	public record FeedbackRequest(@Positive int questionId, @Min(-1) @Max(1) int rating, FlagReason flag,
			@Size(max = 200) String note) {
	}

	public record FeedbackResult(boolean retired) {
	}

	@PostMapping
	public FeedbackResult give(@Valid @RequestBody FeedbackRequest r) {
		return new FeedbackResult(feedback.give(Callers.currentUserId(), Callers.isAccount(), r.questionId(), r.rating(),
				r.flag(), r.note()));
	}

	@GetMapping
	public Review review() {
		return feedback.review();
	}

	@PostMapping("/{questionId}/dismiss")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void dismiss(@PathVariable int questionId) {
		feedback.dismiss(questionId);
	}

	@PostMapping("/{questionId}/restore")
	public Questions restore(@PathVariable int questionId) {
		return feedback.restore(questionId);
	}

	@ExceptionHandler(NotFoundException.class)
	ProblemDetail notFound(RuntimeException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler({ IllegalArgumentException.class,
			InvalidQuestionException.class })
	ProblemDetail invalid(RuntimeException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
	}
}
