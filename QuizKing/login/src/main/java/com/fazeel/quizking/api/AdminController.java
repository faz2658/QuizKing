package com.fazeel.quizking.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fazeel.quizking.account.Account.Role;
import com.fazeel.quizking.account.AccountService;
import com.fazeel.quizking.account.AccountStats;

/** Account numbers for admins. The role is checked against the database, not just the token. */
@RestController
@RequestMapping("/api/v1/auth/admin")
public class AdminController {

	private final AccountService accounts;
	private final AccountStats stats;

	public AdminController(AccountService accounts, AccountStats stats) {
		this.accounts = accounts;
		this.stats = stats;
	}

	@GetMapping("/stats")
	public AccountStats.View stats(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "30") int days) {
		int userId;
		try {
			userId = Integer.parseInt(jwt.getSubject());
		}
		catch (NumberFormatException e) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN);
		}
		boolean admin = accounts.find(userId).map(a -> a.getRole() == Role.ADMIN).orElse(false);
		if (!admin) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN);
		}
		return stats.view(Math.max(1, Math.min(days, 90)));
	}
}
