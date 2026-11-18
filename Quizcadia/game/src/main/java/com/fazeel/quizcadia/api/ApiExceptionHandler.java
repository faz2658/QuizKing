package com.fazeel.quizcadia.api;

import java.net.URI;

import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.fazeel.quizcadia.service.GameNotReadyException;
import com.fazeel.quizcadia.service.NotFoundException;
import com.fazeel.quizcadia.session.QuizRuleException;

// Before FallbackExceptionHandler, which answers whatever this one does not
@RestControllerAdvice
@Order(0)
public class ApiExceptionHandler {

	@ExceptionHandler(NotFoundException.class)
	ProblemDetail notFound(NotFoundException e) {
		return problem(HttpStatus.NOT_FOUND, "not-found", e.getMessage());
	}

	@ExceptionHandler(GameNotReadyException.class)
	ProblemDetail notReady(GameNotReadyException e) {
		ProblemDetail p = problem(HttpStatus.SERVICE_UNAVAILABLE, "game-loading", e.getMessage());
		p.setProperty("retryAfterSeconds", 2);
		return p;
	}

	@ExceptionHandler(QuizRuleException.class)
	ProblemDetail rule(QuizRuleException e) {
		return problem(HttpStatus.CONFLICT, "quiz-rule", e.getMessage());
	}

	@ExceptionHandler(OptimisticLockingFailureException.class)
	ProblemDetail concurrent(OptimisticLockingFailureException e) {
		return problem(HttpStatus.CONFLICT, "concurrent-update", "The session was updated by another request");
	}

	private static ProblemDetail problem(HttpStatus status, String type, String detail) {
		ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
		p.setType(URI.create("https://quizcadia.local/problems/" + type));
		return p;
	}
}
