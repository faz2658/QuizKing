package com.fazeel.quizcadia.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
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

	/** The display name in the token ("name" claim), or "Player" when there is none. */
	public static String currentUserName() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
			String name = jwt.getClaimAsString("name");
			if (name != null && !name.isBlank()) {
				return name.trim();
			}
		}
		return "Player";
	}

	/** A signed-in account (not a guest). */
	public static boolean isAccount() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		return auth != null && auth.getAuthorities().stream()
				.anyMatch(a -> "ROLE_USER".equals(a.getAuthority()) || "ROLE_ADMIN".equals(a.getAuthority()));
	}

	/** The caller is an admin. */
	public static boolean isAdmin() {
		return isAdmin(SecurityContextHolder.getContext().getAuthentication());
	}

	static boolean isAdmin(Authentication auth) {
		return auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
	}
}
