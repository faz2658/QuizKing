package com.fazeel.quizking.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fazeel.quizking.board.LeaderboardService.DailyBoard;
import com.fazeel.quizking.report.GameReport;
import com.fazeel.quizking.report.GameReport.GameMode;
import com.fazeel.quizking.report.GameReportRepository;
import com.fazeel.quizking.report.PlayerNames;

@ExtendWith(MockitoExtension.class)
class LeaderboardServiceTest {

	@Mock
	GameReportRepository reports;
	@Mock
	PlayerNames names;

	static GameReport daily(int user, int score, String finishedAt) {
		return new GameReport("single:" + user, GameMode.SINGLE, user, 0, "Daily Challenge", 0, score, 200, score / 20,
				10, null, null, null, Instant.parse(finishedAt), List.of(), GameReport.DAILY, "2026-11-15", null);
	}

	@Test
	void highestScoreFirstAndTheEarlierFinishWinsATie() {
		when(reports.findByVariantAndVariantKey(GameReport.DAILY, "2026-11-15")).thenReturn(List.of(
				daily(1, 120, "2026-11-15T12:00:00Z"), daily(2, 180, "2026-11-15T13:00:00Z"),
				daily(3, 120, "2026-11-15T09:00:00Z")));
		when(names.lookup(anyCollection())).thenReturn(Map.of(1, "Ada", 2, "Bo", 3, "Cy"));

		DailyBoard board = new LeaderboardService(reports, names).daily(LocalDate.parse("2026-11-15"), 1);

		assertThat(board.top()).extracting(e -> e.name()).containsExactly("Bo", "Cy", "Ada");
		assertThat(board.you().rank()).isEqualTo(3);
		assertThat(board.players()).isEqualTo(3);
	}

	@Test
	void xpBoardRanksThePeriodAndCanFilterOneCategory() {
		Instant now = Instant.parse("2026-11-15T18:00:00Z"); // a Sunday
		GameReport adaToday = new GameReport("1", GameMode.SINGLE, 1, 5, "Art", 9, 50, 100, 5, 10, null, null, null,
				Instant.parse("2026-11-15T10:00:00Z"), List.of());
		GameReport boMonday = new GameReport("2", GameMode.SINGLE, 2, 6, "Maths", 4, 90, 100, 9, 10, null, null, null,
				Instant.parse("2026-11-10T10:00:00Z"), List.of());
		when(reports.findByCompletedAtGreaterThanEqual(Instant.parse("2026-11-09T00:00:00Z")))
				.thenReturn(List.of(adaToday, boMonday));
		when(names.lookup(anyCollection())).thenAnswer(i -> Map.of(1, "Ada", 2, "Bo"));
		LeaderboardService service = new LeaderboardService(reports, names);

		var week = service.xp(LeaderboardService.Period.WEEK, null, 1, now);
		assertThat(week.top()).extracting(e -> e.name()).containsExactly("Bo", "Ada");
		assertThat(week.top().get(0).xp()).isEqualTo(5 + 90);
		assertThat(week.you().rank()).isEqualTo(2);

		var art = service.xp(LeaderboardService.Period.WEEK, 9, 1, now);
		assertThat(art.top()).extracting(e -> e.name()).containsExactly("Ada");
	}

	@Test
	void countriesAreRankedByTheirPlayersXp() {
		Instant now = Instant.parse("2026-11-15T18:00:00Z");
		GameReport ada = new GameReport("1", GameMode.SINGLE, 1, 5, "Art", 9, 50, 100, 5, 10, null, null, null,
				Instant.parse("2026-11-15T10:00:00Z"), List.of());
		GameReport bo = new GameReport("2", GameMode.SINGLE, 2, 6, "Maths", 4, 90, 100, 9, 10, null, null, null,
				Instant.parse("2026-11-14T10:00:00Z"), List.of());
		GameReport cy = new GameReport("3", GameMode.SINGLE, 3, 6, "Maths", 4, 20, 100, 2, 10, null, null, null,
				Instant.parse("2026-11-14T10:00:00Z"), List.of());
		when(reports.findByCompletedAtGreaterThanEqual(Instant.parse("2026-11-09T00:00:00Z"))).thenReturn(List.of(ada, bo, cy));
		when(names.lookup(anyCollection())).thenAnswer(i -> Map.of(1, "Ada", 2, "Bo", 3, "Cy"));
		when(names.countries(anyCollection())).thenAnswer(i -> Map.of(1, "CA", 2, "IN", 3, "CA"));

		var board = new LeaderboardService(reports, names).countries(LeaderboardService.Period.WEEK, now);

		assertThat(board.countries()).extracting(c -> c.country()).containsExactly("IN", "CA");
		assertThat(board.countries().get(1).players()).isEqualTo(2);
		assertThat(board.countries().get(1).xp()).isEqualTo((5 + 50) + (5 + 20));
	}

	@Test
	void placeGivesCountryRankAndTopPercent() {
		List<LeaderboardService.DailyEntry> entries = List.of(
				new LeaderboardService.DailyEntry(1, 1, "A", 90, 100, 9, 10, "IN"),
				new LeaderboardService.DailyEntry(2, 2, "B", 80, 100, 8, 10, "CA"),
				new LeaderboardService.DailyEntry(3, 3, "C", 70, 100, 7, 10, "CA"),
				new LeaderboardService.DailyEntry(4, 4, "D", 60, 100, 6, 10, null));

		LeaderboardService.Place place = LeaderboardService.place(entries.get(2), entries);
		assertThat(place.country()).isEqualTo("CA");
		assertThat(place.countryRank()).isEqualTo(2);
		assertThat(place.countryPlayers()).isEqualTo(2);
		assertThat(place.topPercent()).isEqualTo(75);
		assertThat(LeaderboardService.place(entries.get(3), entries).countryRank()).isZero();
		assertThat(LeaderboardService.place(entries.get(0), entries).topPercent()).isEqualTo(25);
		assertThat(LeaderboardService.place(null, entries)).isNull();
	}
}
