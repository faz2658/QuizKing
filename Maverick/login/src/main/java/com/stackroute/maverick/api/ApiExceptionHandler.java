package com.stackroute.maverick.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.stackroute.maverick.account.BadCredentialsException;
import com.stackroute.maverick.account.EmailTakenException;

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
}
