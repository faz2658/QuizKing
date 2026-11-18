package com.fazeel.quizcadia.session;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;

/**
 * The refresh token travels only in this cookie: HttpOnly (page scripts can't read it), SameSite=Strict (other
 * sites can't make the browser send it), sent only to /api/v1/auth, and Secure when the site is served over HTTPS.
 */
@Component
public class SessionCookie {

	public static final String NAME = "qc_refresh";
	static final String PATH = "/api/v1/auth";

	private final boolean secure;
	private final Clock clock;

	public SessionCookie(@Value("${quizcadia.public-url:http://localhost:4200}") String publicUrl, Clock clock) {
		this.secure = publicUrl.startsWith("https://");
		this.clock = clock;
	}

	public void set(HttpServletResponse response, SessionService.Issued issued) {
		Duration maxAge = Duration.between(clock.instant(), issued.expiresAt());
		response.addHeader(HttpHeaders.SET_COOKIE, cookie(issued.token(), maxAge.isNegative() ? Duration.ZERO : maxAge));
	}

	public void clear(HttpServletResponse response) {
		response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO));
	}

	/** The Set-Cookie value that removes the cookie, for error responses. */
	public String cleared() {
		return cookie("", Duration.ZERO);
	}

	private String cookie(String value, Duration maxAge) {
		return ResponseCookie.from(NAME, value).httpOnly(true).secure(secure).sameSite("Strict").path(PATH)
				.maxAge(maxAge).build().toString();
	}
}
