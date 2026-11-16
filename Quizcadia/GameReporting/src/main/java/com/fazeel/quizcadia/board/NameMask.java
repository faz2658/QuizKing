package com.fazeel.quizcadia.board;

import java.util.List;

import com.fazeel.quizcadia.board.CountryCup.CupView;
import com.fazeel.quizcadia.board.LeaderboardService.DailyBoard;
import com.fazeel.quizcadia.board.LeaderboardService.DailyEntry;
import com.fazeel.quizcadia.board.LeaderboardService.XpBoard;
import com.fazeel.quizcadia.board.LeaderboardService.XpEntry;

/**
 * Hides other players' names on public leaderboards for privacy: "Fazeel" becomes "Faz***". Players still see
 * their own name in full, and admins see every name. Applied to each response, after the shared board cache, so
 * each caller gets their own view of the same board.
 */
public final class NameMask {

	private NameMask() {
	}

	/** First three letters (one for names of three or fewer) and "***"; never reveals the length. */
	public static String mask(String name) {
		if (name == null || name.isBlank()) {
			return "Player***";
		}
		String n = name.strip();
		int letters = n.codePointCount(0, n.length());
		int keep = letters <= 3 ? 1 : 3;
		return n.substring(0, n.offsetByCodePoints(0, keep)) + "***";
	}

	public static XpBoard xp(XpBoard b, int me, boolean admin) {
		if (admin) {
			return b;
		}
		return new XpBoard(b.period(), b.categoryId(), b.players(), b.top().stream().map(e -> xp(e, me)).toList(),
				b.you() == null ? null : xp(b.you(), me), b.all());
	}

	public static DailyBoard daily(DailyBoard b, int me, boolean admin) {
		if (admin) {
			return b;
		}
		return new DailyBoard(b.date(), b.players(), b.top().stream().map(e -> daily(e, me)).toList(),
				b.you() == null ? null : daily(b.you(), me), b.place());
	}

	/** A country's star player has no id here, so it's always masked (except for admins). */
	public static CupView cup(CupView c, boolean admin) {
		if (admin) {
			return c;
		}
		List<CountryCup.Entry> standings = c.standings().stream()
				.map(e -> new CountryCup.Entry(e.rank(), e.country(), e.points(), e.players(), e.star() == null ? null : mask(e.star())))
				.toList();
		return new CupView(c.weekStart(), c.endsAt(), standings, c.countries(), c.yours(), c.lastWeek());
	}

	private static XpEntry xp(XpEntry e, int me) {
		return e.userId() == me ? e
				: new XpEntry(e.rank(), e.userId(), mask(e.name()), e.xp(), e.games(), e.title(), e.country());
	}

	private static DailyEntry daily(DailyEntry e, int me) {
		return e.userId() == me ? e
				: new DailyEntry(e.rank(), e.userId(), mask(e.name()), e.score(), e.maxScore(), e.correctAnswers(),
						e.totalQuestions(), e.country());
	}
}
