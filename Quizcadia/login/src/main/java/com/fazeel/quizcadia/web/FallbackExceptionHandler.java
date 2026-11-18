package com.fazeel.quizcadia.web;

import java.net.URI;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.util.DisconnectedClientHelper;

/**
 * The last word on errors. A copy of quizcadia-security's FallbackExceptionHandler, since the login service doesn't
 * use that module (it signs the tokens the module checks): anything a service's own handler doesn't answer still
 * comes back as RFC 9457 problem details ({@code application/problem+json}) with a {@code detail} the app can show as is.
 *
 * <ul>
 * <li>Spring's own request errors (bad JSON, wrong parameter type, missing parameter, unknown path, wrong method,
 * unsupported media type...) become 400 / 404 / 405 / 415 problems, through {@link ResponseEntityExceptionHandler}.</li>
 * <li>A database or another service that can't be reached becomes 503 with Retry-After and {@code maintenance: true},
 * the same answer nginx gives for a service that is down, so the app shows its "being updated" banner and repeats
 * reads once the servers are back.</li>
 * <li>Anything else becomes 500 with a generic sentence and a short error id; the exception is logged with that id,
 * so a player can quote it. Exception messages, class names and stack traces never reach the browser.</li>
 * <li>Sign-in and permission failures are left to Spring Security (401 / 403), and a player who closed the page
 * mid-answer is not an error at all.</li>
 * </ul>
 *
 * Ordered last: each service's own {@code @RestControllerAdvice} ({@code @Order(0)}) answers its domain errors first.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class FallbackExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(FallbackExceptionHandler.class);

	static final int RETRY_AFTER_SECONDS = 15;
	static final String UNAVAILABLE = "QuizCadia is being updated right now. Please try again in a moment.";
	static final String UNEXPECTED = "Something went wrong on our side. Please try again.";

	/** Let Spring Security answer these (401 / 403) instead of turning them into a 500 below. */
	@ExceptionHandler({ AccessDeniedException.class, AuthenticationException.class })
	void security(RuntimeException e) {
		throw e;
	}

	@ExceptionHandler({ DataAccessResourceFailureException.class, TransientDataAccessException.class,
			RecoverableDataAccessException.class, CannotCreateTransactionException.class,
			ResourceAccessException.class })
	ResponseEntity<ProblemDetail> unavailable(Exception e, WebRequest request) {
		log.warn("Answering 503, a dependency is unreachable ({}): {}", path(request), e.toString());
		ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
		p.setType(URI.create("about:blank"));
		p.setProperty("maintenance", true);
		p.setProperty("retryAfterSeconds", RETRY_AFTER_SECONDS);
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.header(HttpHeaders.RETRY_AFTER, String.valueOf(RETRY_AFTER_SECONDS))
				.header("X-Maintenance", "1")
				.body(p);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDetail> unexpected(Exception e, WebRequest request) {
		if (DisconnectedClientHelper.isClientDisconnectedException(e)) {
			log.debug("Client went away ({}): {}", path(request), e.toString());
			return null; // nobody is listening for an answer
		}
		String errorId = UUID.randomUUID().toString().substring(0, 8);
		log.error("Unexpected error {} ({})", errorId, path(request), e);
		ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
				UNEXPECTED + " (error " + errorId + ")");
		p.setProperty("errorId", errorId);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(p);
	}

	private static String path(WebRequest request) {
		return request.getDescription(false); // "uri=/api/..."
	}
}
