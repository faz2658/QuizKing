package com.fazeel.quizking.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.fazeel.quizking.account.BadCredentialsException;
import com.fazeel.quizking.account.EmailTakenException;
import com.fazeel.quizking.account.InvalidResetLinkException;
import com.fazeel.quizking.google.GoogleSignInException;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(EmailTakenException.class)
	ProblemDetail taken(EmailTakenException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
	}

	@ExceptionHandler(BadCredentialsException.class)
	ProblemDetail badCredentials(BadCredentialsException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
	}

	@ExceptionHandler(InvalidResetLinkException.class)
	ProblemDetail invalidLink(InvalidResetLinkException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
	}

	@ExceptionHandler(GoogleSignInException.class)
	ProblemDetail google(GoogleSignInException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
	}
}
