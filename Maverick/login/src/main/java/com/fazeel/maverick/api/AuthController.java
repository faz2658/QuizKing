package com.fazeel.maverick.api;

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

import com.fazeel.maverick.account.Account;
import com.fazeel.maverick.account.AccountService;
import com.fazeel.maverick.security.TokenService;
import com.fazeel.maverick.security.TokenService.IssuedToken;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AccountService accounts;
	private final TokenService tokens;

	public AuthController(AccountService accounts, TokenService tokens) {
		this.accounts = accounts;
		this.tokens = tokens;
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

	@GetMapping("/me")
	public UserView me(@AuthenticationPrincipal Jwt jwt) {
		return accounts.find(Integer.parseInt(jwt.getSubject())).map(UserView::of)
				.orElseThrow(com.fazeel.maverick.account.BadCredentialsException::new);
	}

	private TokenView token(Account a) {
		IssuedToken t = tokens.issue(a);
		return new TokenView(t.value(), "Bearer", t.expiresAt(), UserView.of(a));
	}
}
