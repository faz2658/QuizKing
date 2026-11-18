package com.fazeel.quizcadia.api;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

/**
 * Turns Bean Validation failures into an RFC 9457 problem: {@code detail} is what the app shows the player,
 * {@code errors} maps each field to its message. One message per field (a blank field says "Enter…", not
 * also "too short"), in the order the fields appear in the request.
 */
final class ValidationProblems {

	static final String UNREADABLE = "The request body is missing or isn't valid JSON.";

	/** "Required" messages win over the others for the same field. */
	private static final Set<String> FIRST = Set.of("NotBlank", "NotNull", "NotEmpty");

	private ValidationProblems() {
	}

	static ProblemDetail of(BindingResult result) {
		List<String> order = fieldOrder(result.getTarget());
		Map<String, String> errors = new LinkedHashMap<>();
		result.getFieldErrors().stream()
				.sorted(Comparator.comparingInt((FieldError f) -> indexOf(order, f.getField()))
						.thenComparing(FieldError::getField)
						.thenComparingInt(f -> FIRST.contains(f.getCode()) ? 0 : 1)
						.thenComparing(f -> String.valueOf(f.getDefaultMessage())))
				.forEach(f -> errors.putIfAbsent(f.getField(), f.getDefaultMessage()));
		result.getGlobalErrors().forEach(g -> errors.putIfAbsent(g.getObjectName(), g.getDefaultMessage()));
		String detail = errors.isEmpty() ? "The request is not valid." : String.join(" ", errors.values());
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
		problem.setTitle("Invalid input");
		problem.setProperty("errors", errors);
		return problem;
	}

	private static List<String> fieldOrder(Object target) {
		if (target == null || !target.getClass().isRecord()) {
			return List.of();
		}
		return Arrays.stream(target.getClass().getRecordComponents()).map(RecordComponent::getName).toList();
	}

	private static int indexOf(List<String> order, String field) {
		int dot = field.indexOf('.');
		int bracket = field.indexOf('[');
		int end = Math.min(dot < 0 ? field.length() : dot, bracket < 0 ? field.length() : bracket);
		int i = order.indexOf(field.substring(0, end));
		return i < 0 ? Integer.MAX_VALUE : i;
	}
}
