package com.fazeel.quizcadia.api;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.fazeel.quizcadia.account.BadCredentialsException;
import com.fazeel.quizcadia.account.EmailTakenException;
import com.fazeel.quizcadia.account.InvalidResetLinkException;
import com.fazeel.quizcadia.account.TooManyAttemptsException;
import com.fazeel.quizcadia.google.GoogleSignInException;
import com.fazeel.quizcadia.session.SessionCookie;
import com.fazeel.quizcadia.session.SessionEndedException;

// Before FallbackExceptionHandler, which answers whatever this one does not
@RestControllerAdvice
@Order(0)
public class ApiExceptionHandler {

	private final SessionCookie cookie;

	ApiExceptionHandler(SessionCookie cookie) {
		this.cookie = cookie;
	}

	/** 401, and the dead refresh cookie is removed so the browser stops sending it. */
	@ExceptionHandler(SessionEndedException.class)
	ResponseEntity<ProblemDetail> sessionEnded(SessionEndedException e) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.header(HttpHeaders.SET_COOKIE, cookie.cleared())
				.body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage()));
	}

	/** 400 with one readable sentence per bad field in {@code detail}, and {@code errors}: field → message. */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail invalid(MethodArgumentNotValidException e) {
		return ValidationProblems.of(e.getBindingResult());
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ProblemDetail unreadable(HttpMessageNotReadableException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ValidationProblems.UNREADABLE);
	}

	@ExceptionHandler(EmailTakenException.class)
	ProblemDetail taken(EmailTakenException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
	}

	@ExceptionHandler(TooManyAttemptsException.class)
	ResponseEntity<ProblemDetail> tooMany(TooManyAttemptsException e) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
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
