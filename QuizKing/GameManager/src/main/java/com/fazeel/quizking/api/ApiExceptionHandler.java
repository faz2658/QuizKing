package com.fazeel.quizking.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.fazeel.quizking.catalog.InvalidGameException;
import com.fazeel.quizking.catalog.NotFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(NotFoundException.class)
	ProblemDetail notFound(NotFoundException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler(InvalidGameException.class)
	ProblemDetail invalid(InvalidGameException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
	}
}
