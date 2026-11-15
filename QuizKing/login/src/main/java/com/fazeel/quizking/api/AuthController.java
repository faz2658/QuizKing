package com.fazeel.quizking.api;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fazeel.quizking.account.Account;
import com.fazeel.quizking.account.AccountService;
import com.fazeel.quizking.account.PasswordResetService;
import com.fazeel.quizking.google.GoogleTokenVerifier;
import com.fazeel.quizking.security.TokenService;
import com.fazeel.quizking.security.TokenService.IssuedToken;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AccountService accounts;
	private final TokenService tokens;
	private final PasswordResetService resets;
	private final GoogleTokenVerifier google;

	public AuthController(AccountService accounts, TokenService tokens, PasswordResetService resets,
			GoogleTokenVerifier google) {
		this.accounts = accounts;
		this.tokens = tokens;
		this.resets = resets;
		this.google = google;
	}

	/** Which sign-in options the browser should show. The Google client id is public by design. */
	public record ProvidersView(String googleClientId) {
	}

	@GetMapping("/providers")
	public ProvidersView providers() {
		return new ProvidersView(google.enabled() ? google.clientId() : null);
	}

	public record GoogleRequest(@NotBlank @Size(max = 4096) String credential) {
	}

	/** "Sign in with Google": the browser sends the ID token Google gave it; new players get an account. */
	@PostMapping("/google")
	public TokenView google(@Valid @RequestBody GoogleRequest r) {
		return token(accounts.signInWithGoogle(google.verify(r.credential())));
	}

	public record ForgotRequest(@NotBlank @Email @Size(max = 254) String email) {

		public ForgotRequest {
			email = email == null ? null : email.trim();
		}
	}

	/** Emails a reset link if the address has an account. Always 202, so it doesn't reveal who has one. */
	@PostMapping("/password/forgot")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void forgot(@Valid @RequestBody ForgotRequest r) {
		resets.requestReset(r.email());
	}

	public record ResetRequest(@NotBlank @Size(max = 200) String token, @NotBlank @Size(min = 8, max = 72) String password) {
	}

	/** Sets a new password from an emailed link and logs the player in. */
	@PostMapping("/password/reset")
	public TokenView reset(@Valid @RequestBody ResetRequest r) {
		return token(resets.reset(r.token(), r.password()));
	}

	public record RegisterRequest(@NotBlank @Size(max = 60) String userName, @NotBlank @Email @Size(max = 254) String email,
			@NotBlank @Size(min = 8, max = 72) String password) {

		public RegisterRequest {
			userName = userName == null ? null : userName.trim();
			email = email == null ? null : email.trim();
		}
	}

	public record LoginRequest(@NotBlank String email, @NotBlank String password) {
	}

	public record UserView(int userId, String userName, String email, String role) {
		static UserView of(Account a) {
			return new UserView(a.getUserId(), a.getUserName(), a.getEmail(), a.getRole().name());
		}
	}

	public record TokenView(String token, String tokenType, Instant expiresAt, UserView user) {
	}

	/** Creates an account and logs the new user in. */
	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public TokenView register(@Valid @RequestBody RegisterRequest r) {
		return token(accounts.register(r.userName(), r.email(), r.password()));
	}

	@PostMapping("/login")
	public TokenView login(@Valid @RequestBody LoginRequest r) {
		return token(accounts.authenticate(r.email(), r.password()));
	}

	public record GuestRequest(@NotBlank @Size(max = 60) String userName) {

		public GuestRequest {
			userName = userName == null ? null : userName.trim();
		}
	}

	/** Lets someone play without an account. Guest ids sit far above real account ids, so they never clash. */
	@PostMapping("/guest")
	@ResponseStatus(HttpStatus.CREATED)
	public TokenView guest(@Valid @RequestBody GuestRequest r) {
		int guestId = GUEST_ID_BASE + RANDOM.nextInt(GUEST_ID_RANGE);
		IssuedToken t = tokens.issueGuest(guestId, r.userName());
		return new TokenView(t.value(), "Bearer", t.expiresAt(),
				new UserView(guestId, r.userName(), "", TokenService.GUEST));
	}

	static final int GUEST_ID_BASE = 900_000_000;
	static final int GUEST_ID_RANGE = 99_999_999;
	private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

	@GetMapping("/me")
	public UserView me(@AuthenticationPrincipal Jwt jwt) {
		return accounts.find(Integer.parseInt(jwt.getSubject())).map(UserView::of)
				.orElseThrow(com.fazeel.quizking.account.BadCredentialsException::new);
	}

	private TokenView token(Account a) {
		IssuedToken t = tokens.issue(a);
		return new TokenView(t.value(), "Bearer", t.expiresAt(), UserView.of(a));
	}
}
