package com.fazeel.quizcadia.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fazeel.quizcadia.report.GameReport;
import com.fazeel.quizcadia.report.GameReport.GameMode;
import com.fazeel.quizcadia.report.GameReportRepository;
import com.fazeel.quizcadia.report.PlayerName;
import com.fazeel.quizcadia.report.PlayerNameRepository;

@ExtendWith(MockitoExtension.class)
class ActivityStatsTest {

	static final Instant NOW = Instant.parse("2026-11-15T18:00:00Z");

	@Mock
	GameReportRepository reports;
	@Mock
	PlayerNameRepository names;

	static GameReport game(int user, String when, String name, String variant) {
		return new GameReport(user + when, GameMode.SINGLE, user, 1, name, 1, 50, 100, 5, 10, null, null, null,
				Instant.parse(when), List.of(), variant, null, null);
	}

	@Test
	void countsActivePlayersGamesAndGuestsSeparately() {
		int guest = ActivityStats.GUEST_ID_BASE + 7;
		when(reports.findCompletedBetween(any(), any())).thenReturn(List.of(
				game(1, "2026-11-15T09:00:00Z", "Art", "DAILY"),
				game(1, "2026-11-15T10:00:00Z", "Art", null),
				game(2, "2026-11-12T10:00:00Z", "History", null),
				game(3, "2026-10-25T10:00:00Z", "Art", null),
				game(guest, "2026-11-15T11:00:00Z", "Art", null)));
		when(reports.count()).thenReturn(42L);
		when(names.findAll()).thenReturn(List.of(new PlayerName(1, "Ada", NOW, "CA"), new PlayerName(2, "Bo", NOW, "CA"),
				new PlayerName(3, "Cy", NOW, "IN"), new PlayerName(guest, "Guesty", NOW, "US")));

		ActivityStats.View v = new ActivityStats(reports, names, Clock.fixed(NOW, ZoneOffset.UTC)).view(30);

		assertThat(v.gamesAllTime()).isEqualTo(42);
		assertThat(v.activeToday()).isEqualTo(1);
		assertThat(v.active7Days()).isEqualTo(2);
		assertThat(v.active30Days()).isEqualTo(3);
		assertThat(v.guestPlayers30Days()).isEqualTo(1);
		assertThat(v.byDay()).hasSize(30);
		ActivityStats.Day today = v.byDay().get(29);
		assertThat(today.day()).isEqualTo(LocalDate.of(2026, 11, 15));
		assertThat(today.games()).isEqualTo(3);
		assertThat(today.players()).isEqualTo(2);
		assertThat(today.guestGames()).isEqualTo(1);
		assertThat(v.modes()).contains(new ActivityStats.Count("Solo", 4), new ActivityStats.Count("Daily", 1));
		assertThat(v.topGames().get(0)).isEqualTo(new ActivityStats.Count("Art", 4));
		assertThat(v.countries()).containsExactly(new ActivityStats.Count("CA", 2), new ActivityStats.Count("IN", 1));
		assertThat(v.topPlayers().get(0).name()).isEqualTo("Ada");
		assertThat(v.topPlayers().get(0).games()).isEqualTo(2);
		assertThat(v.topPlayers()).noneMatch(p -> p.userId() == guest);
	}
}
