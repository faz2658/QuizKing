package com.fazeel.quizcadia.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fazeel.quizcadia.report.GameReport.GameMode;
import com.fazeel.quizcadia.report.ReportService.UserSummary;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

	@Mock
	GameReportRepository repo;

	@InjectMocks
	ReportService service;

	static GameReport report(GameMode mode, int score, int correct, int total, Integer rank) {
		return new GameReport(mode + ":" + score, mode, 1, 1, "g", 1, score, null, correct, total, rank, null, null,
				Instant.EPOCH, List.of());
	}

	@Test
	void summaryAggregatesPerMode() {
		when(repo.findByUserIdOrderByCompletedAtDesc(1)).thenReturn(List.of(report(GameMode.SINGLE, 30, 3, 4, null),
				report(GameMode.SINGLE, 50, 1, 4, null), report(GameMode.MULTI, 20, 2, 2, 1),
				report(GameMode.MULTI, 0, 0, 2, 2)));

		UserSummary s = service.summary(1);

		assertThat(s.gamesPlayed()).isEqualTo(4);
		assertThat(s.multiplayerWins()).isEqualTo(1);
		assertThat(s.modes().get(GameMode.SINGLE).bestScore()).isEqualTo(50);
		assertThat(s.modes().get(GameMode.SINGLE).averageAccuracy()).isEqualTo(50);
		assertThat(s.modes().get(GameMode.ADAPTIVE).gamesPlayed()).isZero();
		assertThat(s.averageAccuracy()).isEqualTo(50);
	}
}
