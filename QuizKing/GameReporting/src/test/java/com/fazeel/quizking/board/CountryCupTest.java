package com.fazeel.quizking.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fazeel.quizking.progress.Xp;
import com.fazeel.quizking.report.GameReport.GameMode;
import com.fazeel.quizking.report.GameReport;
import com.fazeel.quizking.report.GameReportRepository;
import com.fazeel.quizking.report.PlayerNames;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CountryCupTest {

	static final Instant NOW = Instant.parse("2026-11-15T18:00:00Z"); // Sunday: the week began Monday the 9th
	static final Instant MONDAY = Instant.parse("2026-11-09T00:00:00Z");

	@Mock
	GameReportRepository reports;
	@Mock
	PlayerNames names;

	static GameReport game(int user, int score) {
		return new GameReport("r" + user + "-" + score, GameMode.SINGLE, user, 1, "Art", 1, score, 100, score / 10, 10, null,
				null, null, Instant.parse("2026-11-12T10:00:00Z"), List.of());
	}

	CountryCup cup(List<GameReport> week, List<GameReport> lastWeek, Map<Integer, String> countries) {
		when(reports.findByCompletedAtGreaterThanEqualAndCompletedAtLessThan(eq(MONDAY), any())).thenReturn(week);
		when(reports.findByCompletedAtGreaterThanEqualAndCompletedAtLessThan(eq(MONDAY.minusSeconds(7 * 86400)), eq(MONDAY)))
				.thenReturn(lastWeek);
		when(names.countries(anyCollection())).thenAnswer(i -> {
			Map<Integer, String> out = new HashMap<>();
			for (Object id : (java.util.Collection<?>) i.getArgument(0)) {
				if (countries.containsKey(id)) out.put((Integer) id, countries.get(id));
			}
			return out;
		});
		when(names.lookup(anyCollection())).thenAnswer(i -> Map.of(1, "Ada", 2, "Bo", 3, "Cy", 4, "Di"));
		return new CountryCup(reports, names, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void ranksCountriesByTheirPlayersXpAndShowsTheGapToCatchUp() {
		var view = cup(List.of(game(1, 90), game(2, 40), game(3, 30), game(4, 80), game(-5, 100)),
				List.of(game(4, 10)), Map.of(1, "IN", 2, "CA", 3, "CA", 4, "BR")).view(2);

		assertThat(view.standings()).extracting(CountryCup.Entry::country).containsExactly("IN", "BR", "CA");
		assertThat(view.standings().get(2).points()).isEqualTo(Xp.of(game(2, 40)) + Xp.of(game(3, 30)));
		assertThat(view.standings().get(0).star()).isEqualTo("Ada");
		assertThat(view.yours().country()).isEqualTo("CA");
		assertThat(view.yours().rank()).isEqualTo(3);
		assertThat(view.yours().gapToNext()).isEqualTo(view.standings().get(1).points() - view.standings().get(2).points() + 1);
		assertThat(view.yours().yourRank()).isEqualTo(1);
		assertThat(view.lastWeek().country()).isEqualTo("BR");
		assertThat(view.endsAt()).isEqualTo(Instant.parse("2026-11-16T00:00:00Z"));
	}

	@Test
	void onlyTheBestSquadCountsAndPlayersWithoutACountryArentInIt() {
		List<GameReport> week = new ArrayList<>();
		Map<Integer, String> where = new HashMap<>();
		for (int id = 1; id <= CountryCup.SQUAD + 5; id++) {
			week.add(game(id, 10));
			where.put(id, "CA");
		}
		week.add(game(99, 100));
		var view = cup(week, List.of(), where).view(99);

		assertThat(view.standings().get(0).points()).isEqualTo(CountryCup.SQUAD * Xp.of(game(1, 10)));
		assertThat(view.standings().get(0).players()).isEqualTo(CountryCup.SQUAD + 5);
		assertThat(view.yours()).isNull();
		assertThat(view.lastWeek()).isNull();
	}
}
