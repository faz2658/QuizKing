package com.fazeel.quizcadia.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.fazeel.quizcadia.account.BadCredentialsException;
import com.fazeel.quizcadia.account.EmailTakenException;
import com.fazeel.quizcadia.account.InvalidResetLinkException;
import com.fazeel.quizcadia.google.GoogleSignInException;

@RestControllerAdvice
public class ApiExceptionHandler {

	private final com.fazeel.quizcadia.session.SessionCookie cookie;

	ApiExceptionHandler(com.fazeel.quizcadia.session.SessionCookie cookie) {
		this.cookie = cookie;
	}

	/** 401, and the dead refresh cookie is removed so the browser stops sending it. */
	@ExceptionHandler(com.fazeel.quizcadia.session.SessionEndedException.class)
	org.springframework.http.ResponseEntity<ProblemDetail> sessionEnded(com.fazeel.quizcadia.session.SessionEndedException e) {
		return org.springframework.http.ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.header(org.springframework.http.HttpHeaders.SET_COOKIE, cookie.cleared())
				.body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage()));
	}

	@ExceptionHandler(EmailTakenException.class)
	ProblemDetail taken(EmailTakenException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
	}

	@ExceptionHandler(com.fazeel.quizcadia.account.TooManyAttemptsException.class)
	org.springframework.http.ResponseEntity<ProblemDetail> tooMany(com.fazeel.quizcadia.account.TooManyAttemptsException e) {
		return org.springframework.http.ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.header("Retry-After", String.valueOf(e.retryAfterSeconds()))
				.body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage()));
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
