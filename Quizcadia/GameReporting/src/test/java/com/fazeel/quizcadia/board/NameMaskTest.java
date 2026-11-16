package com.fazeel.quizcadia.board;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fazeel.quizcadia.board.CountryCup.CupView;
import com.fazeel.quizcadia.board.LeaderboardService.DailyBoard;
import com.fazeel.quizcadia.board.LeaderboardService.DailyEntry;
import com.fazeel.quizcadia.board.LeaderboardService.Period;
import com.fazeel.quizcadia.board.LeaderboardService.XpBoard;
import com.fazeel.quizcadia.board.LeaderboardService.XpEntry;

class NameMaskTest {

	@Test
	void keepsTheStartAndHidesTheRest() {
		assertThat(NameMask.mask("Fazeel")).isEqualTo("Faz***");
		assertThat(NameMask.mask("  Ada Lovelace ")).isEqualTo("Ada***");
		assertThat(NameMask.mask("Bo")).isEqualTo("B***");
		assertThat(NameMask.mask("Max")).isEqualTo("M***");
		assertThat(NameMask.mask("Zoë")).isEqualTo("Z***");
		assertThat(NameMask.mask("😀😀😀😀")).isEqualTo("😀😀😀***"); // whole characters, never half an emoji
		assertThat(NameMask.mask(null)).isEqualTo("Player***");
		assertThat(NameMask.mask(" ")).isEqualTo("Player***");
	}

	XpEntry xp(int rank, int id, String name) {
		return new XpEntry(rank, id, name, 100 - rank, 3, "Scholar", "CA");
	}

	@Test
	void xpBoardMasksOthersButNotTheCaller() {
		XpBoard board = new XpBoard(Period.WEEK, null, 3, List.of(xp(1, 7, "Fazeel"), xp(2, 8, "Ada Lovelace")), xp(2, 8, "Ada Lovelace"),
				List.of());
		XpBoard seenByAda = NameMask.xp(board, 8, false);
		assertThat(seenByAda.top()).extracting(XpEntry::name).containsExactly("Faz***", "Ada Lovelace");
		assertThat(seenByAda.you().name()).isEqualTo("Ada Lovelace");
		assertThat(seenByAda.top().get(0)).extracting(XpEntry::xp, XpEntry::country).containsExactly(99, "CA");

		assertThat(NameMask.xp(board, 1, true).top()).extracting(XpEntry::name).containsExactly("Fazeel", "Ada Lovelace");
	}

	@Test
	void dailyBoardAndCupAreMaskedToo() {
		DailyEntry fazeel = new DailyEntry(1, 7, "Fazeel", 300, 350, 9, 10, "CA");
		DailyBoard daily = new DailyBoard("2026-11-16", 1, List.of(fazeel), null, null);
		assertThat(NameMask.daily(daily, 99, false).top().get(0).name()).isEqualTo("Faz***");
		assertThat(NameMask.daily(daily, 7, false).top().get(0).name()).isEqualTo("Fazeel");
		assertThat(NameMask.daily(daily, 99, true).top().get(0).name()).isEqualTo("Fazeel");

		CupView cup = new CupView(Instant.EPOCH, Instant.EPOCH, List.of(new CountryCup.Entry(1, "CA", 50, 2, "Fazeel")), 1, null, null);
		assertThat(NameMask.cup(cup, false).standings().get(0).star()).isEqualTo("Faz***");
		assertThat(NameMask.cup(cup, true).standings().get(0).star()).isEqualTo("Fazeel");
	}
}
