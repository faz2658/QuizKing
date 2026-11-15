package com.fazeel.maverick.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

/** Who is calling: the user id in the verified token, never one the client sends. */
public final class Callers {

	private Callers() {
	}

	public static int currentUserId() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated()) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
		}
		try {
			return Integer.parseInt(auth.getName());
		}
		catch (NumberFormatException e) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
		}
	}

	/** Fails with 403 unless {@code userId} is the caller's own id (admins may act for anyone). */
	public static void requireSelf(int userId) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (currentUserId() != userId && !isAdmin(auth)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "That belongs to another player");
		}
	}

	static boolean isAdmin(Authentication auth) {
		return auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
	}
}
