package com.fazeel.quizcadia.inquiry;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fazeel.quizcadia.inquiry.Inquiry.Kind;
import com.fazeel.quizcadia.inquiry.Inquiry.Status;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Requests for private quiz nights and venue trivia. Anyone may send one (no account needed); only admins can
 * list them or mark where they are with them.
 */
@RestController
@RequestMapping("/api/v1/reports/inquiries")
public class InquiryController {

	/** What the website form sends. {@code website} is a hidden field people never see: only bots fill it in. */
	public record InquiryRequest(@NotNull Kind kind,
			@NotBlank @Size(max = 80) String name,
			@NotBlank @Email @Size(max = 120) String email,
			@Size(max = 120) String organisation,
			@Size(max = 40) String occasion,
			@Pattern(regexp = "^$|^\\d{4}-\\d{2}-\\d{2}$") String date,
			@Min(1) @Max(5000) Integer groupSize,
			@Size(max = 80) String city,
			@Size(max = 2000) String message,
			@Size(max = 200) String website) {
	}

	public record StatusChange(@NotNull Status status) {
	}

	private final InquiryRepository repo;
	private final InquiryLimiter limiter;
	private final Clock clock;

	public InquiryController(InquiryRepository repo, InquiryLimiter limiter, Clock clock) {
		this.repo = repo;
		this.limiter = limiter;
		this.clock = clock;
	}

	@PostMapping
	public ResponseEntity<Map<String, Boolean>> send(@Valid @RequestBody InquiryRequest r,
			@RequestHeader(value = "X-Real-IP", required = false) String realIp,
			@RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor, HttpServletRequest http) {
		if (r.website() != null && !r.website().isBlank()) {
			return ResponseEntity.accepted().body(Map.of("received", true)); // a bot: pretend all went well
		}
		if (!limiter.allow(sender(realIp, forwardedFor, http))) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please try again later.");
		}
		var now = clock.instant();
		repo.save(new Inquiry(null, r.kind(), r.name().strip(), r.email().strip(), blankToNull(r.organisation()),
				blankToNull(r.occasion()), blankToNull(r.date()), r.groupSize(), blankToNull(r.city()), blankToNull(r.message()),
				Status.NEW, now, now));
		return ResponseEntity.accepted().body(Map.of("received", true));
	}

	/** Newest first (the latest 200). */
	@GetMapping
	public List<Inquiry> list() {
		return repo.findTop200ByOrderByCreatedAtDesc();
	}

	@PatchMapping("/{id}")
	public Inquiry status(@PathVariable String id, @Valid @RequestBody StatusChange change) {
		Inquiry i = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		return repo.save(i.withStatus(change.status(), clock.instant()));
	}

	/**
	 * The visitor's address: X-Real-IP, which nginx sets from the address Caddy recorded (visitors can't set it),
	 * else the first X-Forwarded-For entry, else the connection's.
	 */
	static String sender(String realIp, String forwardedFor, HttpServletRequest http) {
		if (realIp != null && !realIp.isBlank()) {
			return realIp.strip();
		}
		if (forwardedFor != null && !forwardedFor.isBlank()) {
			return forwardedFor.split(",")[0].strip();
		}
		return http.getRemoteAddr();
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.strip();
	}
}
