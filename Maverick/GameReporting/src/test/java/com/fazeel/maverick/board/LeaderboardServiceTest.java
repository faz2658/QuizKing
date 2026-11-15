package com.fazeel.maverick.board;

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

import com.fazeel.maverick.board.LeaderboardService.DailyBoard;
import com.fazeel.maverick.report.GameReport;
import com.fazeel.maverick.report.GameReport.GameMode;
import com.fazeel.maverick.report.GameReportRepository;
import com.fazeel.maverick.report.PlayerNames;

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
}
