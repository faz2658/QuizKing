package com.fazeel.quizcadia.account;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import com.fazeel.quizcadia.account.Account.Role;

/** Sign-up numbers for the admin dashboard. Small site: it reads every account, which is fine for thousands. */
@Service
public class AccountStats {

	public static final int RECENT = 25;

	private final AccountRepository accounts;
	private final Clock clock;

	public AccountStats(AccountRepository accounts, Clock clock) {
		this.accounts = accounts;
		this.clock = clock;
	}

	public record DayCount(LocalDate day, int count) {
	}

	public record Member(int userId, String userName, String email, Instant createdAt, boolean google, boolean admin) {
	}

	public record View(int total, int google, int password, int admins, int today, int last7Days, int last30Days,
			List<DayCount> signupsByDay, List<Member> recent) {
	}

	/** Totals, sign-ups per UTC day for the last {@code days} days (oldest first) and the newest accounts. */
	public View view(int days) {
		List<Account> all = accounts.findAll();
		LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
		Map<LocalDate, Integer> perDay = new TreeMap<>();
		for (int i = days - 1; i >= 0; i--) {
			perDay.put(today.minusDays(i), 0);
		}
		int google = 0, admins = 0, d1 = 0, d7 = 0, d30 = 0;
		for (Account a : all) {
			if (a.getGoogleSubject() != null) google++;
			if (a.getRole() == Role.ADMIN) admins++;
			LocalDate day = a.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
			perDay.computeIfPresent(day, (k, v) -> v + 1);
			if (day.equals(today)) d1++;
			if (!day.isBefore(today.minusDays(6))) d7++;
			if (!day.isBefore(today.minusDays(29))) d30++;
		}
		List<DayCount> series = new ArrayList<>();
		perDay.forEach((day, n) -> series.add(new DayCount(day, n)));
		List<Member> recent = all.stream().sorted(Comparator.comparing(Account::getCreatedAt).reversed()).limit(RECENT)
				.map(a -> new Member(a.getUserId(), a.getUserName(), a.getEmail(), a.getCreatedAt(),
						a.getGoogleSubject() != null, a.getRole() == Role.ADMIN))
				.toList();
		return new View(all.size(), google, all.size() - google, admins, d1, d7, d30, series, recent);
	}
}
