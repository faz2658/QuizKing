package com.fazeel.quizcadia.api;

import static com.fazeel.quizcadia.api.InputRules.*;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fazeel.quizcadia.account.Account;
import com.fazeel.quizcadia.account.AccountService;
import com.fazeel.quizcadia.account.PasswordResetService;
import com.fazeel.quizcadia.google.GoogleTokenVerifier;
import com.fazeel.quizcadia.security.TokenService;
import com.fazeel.quizcadia.security.TokenService.IssuedToken;
import com.fazeel.quizcadia.session.SessionCookie;
import com.fazeel.quizcadia.session.SessionService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Signing up, in and out: email and password, Google (popup or redirect), guests, password resets, and swapping
 * the refresh cookie for a new access token.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AccountService accounts;
	private final TokenService tokens;
	private final PasswordResetService resets;
	private final GoogleTokenVerifier google;
	private final SessionService sessions;
	private final SessionCookie cookie;

	private final String analyticsId;
	@Value("#{!'${quizcadia.mail.host:}'.isBlank()}")
	private boolean mailConfigured;

	public AuthController(AccountService accounts, TokenService tokens, PasswordResetService resets,
			GoogleTokenVerifier google, SessionService sessions, SessionCookie cookie,
			@Value("${quizcadia.analytics-id:}") String analyticsId) {
		this.analyticsId = analyticsId == null || !analyticsId.matches("G-[A-Z0-9]{4,20}") ? null : analyticsId;
		this.accounts = accounts;
		this.tokens = tokens;
		this.resets = resets;
		this.google = google;
		this.sessions = sessions;
		this.cookie = cookie;
	}

	/**
	 * Which sign-in options the browser should show, and the analytics id. Both are public by design.
	 * {@code passwordReset}: reset links can be emailed (MAIL_HOST is set); otherwise the app hides "Forgot password?".
	 */
	public record ProvidersView(String googleClientId, String analyticsId, boolean passwordReset) {
	}

	/** The same for everyone and only changes with a deploy, so browsers and a CDN may keep it for 5 minutes. */
	@GetMapping("/providers")
	public ResponseEntity<ProvidersView> providers() {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
				.body(new ProvidersView(google.enabled() ? google.clientId() : null, analyticsId, mailConfigured));
	}

	/** Google's ID token from the popup, and a friend's invite code if any. */
	public record GoogleRequest(@NotBlank @Size(max = 4096) String credential, @Size(max = 16) String invite) {
	}

	/** "Sign in with Google": the browser sends the ID token Google gave it; new players get an account. */
	@PostMapping("/google")
	public TokenView google(@Valid @RequestBody GoogleRequest r, HttpServletResponse response) {
		return signIn(accounts.signInWithGoogle(google.verify(r.credential()), r.invite()), response);
	}

	/** Set by the app before a redirect sign-in, so a friend's invite survives the trip to Google and back. */
	public static final String INVITE_COOKIE = "qc_invite";
	/** Google's double-submit token: the same value arrives as a cookie (set on our site) and in the form. */
	static final String GOOGLE_CSRF = "g_csrf_token";
	private static final Logger log = LoggerFactory.getLogger(AuthController.class);

	/**
	 * "Sign in with Google" in redirect mode (iPhone, iPad and Safari, where Google's popup tab can't hand the
	 * sign-in back to a page iOS has frozen in the background). Google posts the ID token here as a form; we check
	 * Google's anti-forgery token, sign the player in exactly like {@link #google} (the refresh cookie) and send the
	 * browser back to the app, which picks the session up from that cookie. Always a 303: the app shows failures.
	 */
	@PostMapping(path = "/google/redirect", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
	public ResponseEntity<Void> googleRedirect(
			@RequestParam(required = false) String credential,
			@RequestParam(name = GOOGLE_CSRF, required = false) String csrfForm,
			@CookieValue(name = GOOGLE_CSRF, required = false) String csrfCookie,
			@CookieValue(name = INVITE_COOKIE, required = false) String invite, HttpServletResponse response) {
		String outcome = "failed";
		if (credential == null || credential.isBlank() || credential.length() > 4096) {
			log.info("Google redirect sign-in without a credential");
		}
		else if (!sameToken(csrfForm, csrfCookie)) {
			log.info("Google redirect sign-in with a missing or mismatched {}", GOOGLE_CSRF);
		}
		else {
			try {
				String code = invite != null && invite.matches("[A-Za-z0-9_-]{1,16}") ? invite : null;
				signIn(accounts.signInWithGoogle(google.verify(credential), code), response);
				outcome = "ok";
			}
			catch (RuntimeException e) {
				log.info("Google redirect sign-in failed: {}", e.getMessage());
			}
		}
		response.addHeader(HttpHeaders.SET_COOKIE,
				ResponseCookie.from(INVITE_COOKIE, "").path("/api/v1/auth").maxAge(0).build().toString());
		return ResponseEntity.status(HttpStatus.SEE_OTHER)
				.location(URI.create("/welcome?google=" + outcome)).build();
	}

	/** Both present and equal, compared in constant time. */
	static boolean sameToken(String a, String b) {
		return a != null && b != null && !a.isEmpty()
				&& MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),
						b.getBytes(StandardCharsets.UTF_8));
	}

	/** The email to send a reset link to. */
	public record ForgotRequest(
			@NotBlank(message = EMAIL_REQUIRED) @Pattern(regexp = EMAIL_LOOSE_PATTERN, message = EMAIL_INVALID)
			@Size(max = EMAIL_MAX, message = EMAIL_SIZE) String email) {

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

	/** The token from the reset link and the new password. */
	public record ResetRequest(
			@NotBlank(message = "This reset link is incomplete. Open the link from the email again.")
			@Size(max = 200, message = "This reset link isn't valid. Ask for a new one.") String token,
			@NotBlank(message = PASSWORD_REQUIRED)
			@Size(min = PASSWORD_MIN, max = PASSWORD_MAX, message = PASSWORD_SIZE) String password) {
	}

	/** Sets a new password from an emailed link and logs the player in. */
	@PostMapping("/password/reset")
	public TokenView reset(@Valid @RequestBody ResetRequest r, HttpServletResponse response) {
		return signIn(resets.reset(r.token(), r.password()), response); // the reset ended every other sign-in
	}

	/** Name, email and password for a new account, and a friend's invite code if any. Name and email are trimmed. */
	public record RegisterRequest(
			@NotBlank(message = NAME_REQUIRED) @Size(max = NAME_MAX, message = NAME_SIZE)
			@Pattern(regexp = NAME_PATTERN, message = NAME_CHARS) String userName,
			@NotBlank(message = EMAIL_REQUIRED) @Email(message = EMAIL_INVALID)
			@Pattern(regexp = EMAIL_PATTERN, message = EMAIL_INVALID) @Size(max = EMAIL_MAX, message = EMAIL_SIZE) String email,
			@NotBlank(message = PASSWORD_REQUIRED)
			@Size(min = PASSWORD_MIN, max = PASSWORD_MAX, message = PASSWORD_SIZE) String password,
			// a broken invite never stops a sign-up (AccountService.referrer ignores it), so only the length is checked
			@Size(max = 16) String invite) {

		public RegisterRequest {
			userName = userName == null ? null : userName.trim();
			email = email == null ? null : email.trim();
		}
	}

	/** Email and password to sign in with (the email trimmed). */
	public record LoginRequest(
			@NotBlank(message = EMAIL_REQUIRED) @Pattern(regexp = EMAIL_LOOSE_PATTERN, message = EMAIL_INVALID)
			@Size(max = EMAIL_MAX, message = EMAIL_SIZE) String email,
			@NotBlank(message = "Enter your password.") @Size(max = PASSWORD_MAX, message = PASSWORD_TOO_LONG) String password) {

		public LoginRequest {
			email = email == null ? null : email.trim();
		}
	}

	/** The signed-in player as the app knows them: id, name, email and role. */
	public record UserView(int userId, String userName, String email, String role) {
		static UserView of(Account a) {
			return new UserView(a.getUserId(), a.getUserName(), a.getEmail(), a.getRole().name());
		}
	}

	/**
	 * {@code expiresAt}: when the access token runs out (minutes). {@code sessionExpiresAt}: when the refresh
	 * cookie set with this response does (null when no new cookie was set), so the app knows how long it stays signed in.
	 */
	public record TokenView(String token, String tokenType, Instant expiresAt, UserView user, Instant sessionExpiresAt) {
	}

	/** Creates an account and logs the new user in. */
	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public TokenView register(@Valid @RequestBody RegisterRequest r, HttpServletResponse response) {
		return signIn(accounts.register(r.userName(), r.email(), r.password(), r.invite()), response);
	}

	/** Signs in with email and password: an access token in the reply and the refresh cookie. */
	@PostMapping("/login")
	public TokenView login(@Valid @RequestBody LoginRequest r, HttpServletResponse response) {
		return signIn(accounts.authenticate(r.email(), r.password()), response);
	}

	/**
	 * A new short-lived access token from the refresh cookie (which is swapped for the next one). The app calls
	 * this shortly before its access token runs out, and after a 401. 401 when the sign-in has ended.
	 */
	@PostMapping("/refresh")
	public TokenView refresh(@CookieValue(name = SessionCookie.NAME, required = false) String refreshToken,
			HttpServletResponse response) {
		SessionService.Renewal r = sessions.refresh(refreshToken);
		if (r.refresh() != null) {
			cookie.set(response, r.refresh());
		}
		return new TokenView(r.access().value(), "Bearer", r.access().expiresAt(),
				new UserView(r.userId(), r.userName(), r.email(), r.role()),
				r.refresh() == null ? null : r.refresh().expiresAt());
	}

	/** Signs this browser out: the refresh token stops working and the cookie is removed. Always 204. */
	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(@CookieValue(name = SessionCookie.NAME, required = false) String refreshToken,
			HttpServletResponse response) {
		sessions.end(refreshToken);
		cookie.clear(response);
	}

	/** Signs the account out on every device (a guest just signs out here). */
	@PostMapping("/logout/all")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logoutEverywhere(@AuthenticationPrincipal Jwt jwt,
			@CookieValue(name = SessionCookie.NAME, required = false) String refreshToken, HttpServletResponse response) {
		List<String> roles = jwt.getClaimAsStringList("roles");
		if (roles != null && roles.contains(TokenService.GUEST)) {
			sessions.end(refreshToken);
		}
		else {
			sessions.endAll(Integer.parseInt(jwt.getSubject()));
		}
		cookie.clear(response);
	}

	/** The name a guest plays under (trimmed). */
	public record GuestRequest(
			@NotBlank(message = NAME_REQUIRED) @Size(max = NAME_MAX, message = NAME_SIZE)
			@Pattern(regexp = NAME_PATTERN, message = NAME_CHARS) String userName) {

		public GuestRequest {
			userName = userName == null ? null : userName.trim();
		}
	}

	/** Lets someone play without an account. Guest ids sit far above real account ids, so they never clash. */
	@PostMapping("/guest")
	@ResponseStatus(HttpStatus.CREATED)
	public TokenView guest(@Valid @RequestBody GuestRequest r, HttpServletResponse response) {
		int guestId = GUEST_ID_BASE + RANDOM.nextInt(GUEST_ID_RANGE);
		IssuedToken t = tokens.issueGuest(guestId, r.userName());
		SessionService.Issued session = sessions.startGuest(guestId, r.userName());
		cookie.set(response, session);
		return new TokenView(t.value(), "Bearer", t.expiresAt(),
				new UserView(guestId, r.userName(), "", TokenService.GUEST), session.expiresAt());
	}

	static final int GUEST_ID_BASE = 900_000_000;
	static final int GUEST_ID_RANGE = 99_999_999;
	private static final SecureRandom RANDOM = new SecureRandom();

	/** The signed-in player: their account, or for a guest what the guest token says (guests have no account). */
	@GetMapping("/me")
	public UserView me(@AuthenticationPrincipal Jwt jwt) {
		int userId = Integer.parseInt(jwt.getSubject());
		List<String> roles = jwt.getClaimAsStringList("roles");
		if (roles != null && roles.contains(TokenService.GUEST)) {
			return new UserView(userId, jwt.getClaimAsString("name"), "", TokenService.GUEST);
		}
		return accounts.find(userId).map(UserView::of)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
						"This account no longer exists. Please sign in again."));
	}

	/** A short access token in the body, and a new sign-in in the refresh cookie. */
	private TokenView signIn(Account a, HttpServletResponse response) {
		SessionService.Issued session = sessions.start(a);
		cookie.set(response, session);
		IssuedToken t = tokens.issue(a);
		return new TokenView(t.value(), "Bearer", t.expiresAt(), UserView.of(a), session.expiresAt());
	}
}
