package com.fazeel.quizking.arena;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.fazeel.quizking.arena.ArenaEntry.Answer;
import com.fazeel.quizking.daily.DailyChallengeService;
import com.fazeel.quizking.domain.PlayedQuestion;
import com.fazeel.quizking.domain.SinglePlayerResult;
import com.fazeel.quizking.kafka.GameEvents;
import com.fazeel.quizking.repository.GameRepository;
import com.fazeel.quizking.session.QuizRuleException;
import com.fazeel.quizking.session.SessionQuestion;

/** The World Arena: scheduled rounds that everyone online plays at once. See {@link ArenaRules}. */
@Service
public class ArenaService {

	public static final String NAME = "World Arena";

	private final ArenaRoundRepository rounds;
	private final ArenaEntryRepository entries;
	private final ArenaPublishedRepository published;
	private final GameRepository games;
	private final GameEvents events;
	private final Clock clock;

	public ArenaService(ArenaRoundRepository rounds, ArenaEntryRepository entries, ArenaPublishedRepository published, GameRepository games, GameEvents events,
			Clock clock) {
		this.rounds = rounds;
		this.entries = entries;
		this.published = published;
		this.games = games;
		this.events = events;
		this.clock = clock;
	}

	public enum Phase {
		/** Before the join window. */
		SOON,
		/** Joining is open. */
		JOINING,
		LIVE,
		FINISHED
	}

	public record QuestionView(int index, int number, int total, String stem, List<String> options, long secondsLeft,
			String yourAnswer) {
	}

	public record Reveal(int index, String stem, String correctAnswer, String yourAnswer, long eliminatedNow) {
	}

	public record Standing(int rank, int userId, String name, String country, int survived, int score) {
	}

	public record You(boolean joined, boolean alive, int survived, int score, Integer rank) {
	}

	public record RoundView(String roundId, Phase phase, Instant startsAt, Instant joinOpensAt, int players, long alive,
			You you, QuestionView question, Reveal reveal, List<Standing> standings, Instant endsAt) {
	}

	public record ArenaView(Instant serverTime, RoundView round, RoundView previous) {
	}

	public ArenaView view(int userId) {
		Instant now = clock.instant();
		Instant s = ArenaRules.roundStart(now);
		List<ArenaEntry> current = entries.findByRoundId(id(s));
		boolean currentRunning = !current.isEmpty()
				&& now.isBefore(ArenaRules.endsAt(s, ArenaRules.lastQuestion(s, current, now)));
		Instant active = currentRunning ? s : s.plus(ArenaRules.INTERVAL);
		Instant previous = active.minus(ArenaRules.INTERVAL);
		List<ArenaEntry> previousEntries = active.equals(s) ? entries.findByRoundId(id(previous)) : current;
		RoundView prev = previousEntries.isEmpty() ? null : roundView(previous, previousEntries, userId, now);
		if (prev != null && prev.phase() == Phase.FINISHED) {
			publish(previous, previousEntries, now);
		}
		return new ArenaView(now, roundView(active, active.equals(s) ? current : entries.findByRoundId(id(active)), userId, now),
				prev);
	}

	public ArenaView join(int userId, String name, String country) {
		Instant now = clock.instant();
		Instant start = ArenaRules.roundStart(now).plus(ArenaRules.INTERVAL);
		if (now.isBefore(start.minus(ArenaRules.JOIN_BEFORE))) {
			throw new QuizRuleException("Joining opens 10 minutes before the round starts");
		}
		String round = id(start);
		Optional<ArenaEntry> existing = entries.findById(round + ":" + userId);
		if (existing.isEmpty()) {
			entries.save(new ArenaEntry(round, userId, name, validCountry(country), now));
		} else if (country != null && validCountry(country) != null) {
			existing.get().setCountry(validCountry(country));
			entries.save(existing.get());
		}
		roundFor(start); // fix the questions now, while joining, so the round starts instantly
		return view(userId);
	}

	public ArenaView answer(int userId, int index, String answer) {
		Instant now = clock.instant();
		Instant start = ArenaRules.roundStart(now);
		ArenaEntry entry = entries.findById(id(start) + ":" + userId)
				.orElseThrow(() -> new QuizRuleException("You're not in this round"));
		List<ArenaEntry> all = entries.findByRoundId(id(start));
		int last = ArenaRules.lastQuestion(start, all, now);
		if (index < 0 || index > last || now.isBefore(ArenaRules.opens(start, index))
				|| !now.isBefore(ArenaRules.closes(start, index))) {
			throw new QuizRuleException("That question isn't open");
		}
		if (entry.survived(index) < index) {
			throw new QuizRuleException("You're out of this round: watch the rest!");
		}
		if (entry.answer(index) != null) {
			throw new QuizRuleException("You already answered");
		}
		SessionQuestion q = roundFor(start).questions().get(index);
		boolean correct = answer != null && answer.trim().equalsIgnoreCase(q.getCorrectAnswer().trim());
		entry.record(index, new Answer(answer, correct, now, correct ? ArenaRules.points(ArenaRules.opens(start, index), now) : 0));
		entries.save(entry);
		return view(userId);
	}

	private RoundView roundView(Instant start, List<ArenaEntry> roundEntries, int userId, Instant now) {
		Instant joinOpens = start.minus(ArenaRules.JOIN_BEFORE);
		int closed = ArenaRules.closedCount(start, now);
		int last = ArenaRules.lastQuestion(start, roundEntries, now);
		Instant ends = ArenaRules.endsAt(start, last);
		Phase phase = now.isBefore(joinOpens) ? Phase.SOON : now.isBefore(start) ? Phase.JOINING
				: now.isBefore(ends) ? Phase.LIVE : Phase.FINISHED;
		if (phase == Phase.LIVE && roundEntries.isEmpty()) {
			phase = Phase.FINISHED; // nobody joined: nothing to play
		}
		Optional<ArenaEntry> me = roundEntries.stream().filter(e -> e.getUserId() == userId).findFirst();
		int shownClosed = Math.min(closed, last + 1);
		long alive = roundEntries.stream().filter(e -> e.survived(shownClosed) >= shownClosed).count();
		List<ArenaEntry> ranked = new ArrayList<>(roundEntries);
		ranked.sort(ArenaRules.ranking(shownClosed));
		Integer myRank = null;
		for (int i = 0; i < ranked.size(); i++) {
			if (ranked.get(i).getUserId() == userId) {
				myRank = i + 1;
			}
		}
		You you = me.map(e -> new You(true, e.survived(shownClosed) >= shownClosed, e.survived(shownClosed), e.score(), null))
				.orElse(new You(false, false, 0, 0, null));
		if (phase == Phase.FINISHED && me.isPresent()) {
			you = new You(true, myRank == 1, me.get().survived(shownClosed), me.get().score(), myRank);
		}

		QuestionView question = null;
		Reveal reveal = null;
		if (phase == Phase.LIVE) {
			ArenaRound round = roundFor(start);
			final int index = (int) Math.min(last, Duration.between(start, now).dividedBy(ArenaRules.SLOT));
			SessionQuestion q = round.questions().get(index);
			Instant opened = ArenaRules.opens(start, index);
			String mine = me.map(e -> e.answer(index)).map(Answer::answer).orElse(null);
			if (now.isBefore(opened.plus(ArenaRules.ANSWER_TIME))) {
				long left = Math.max(0, Duration.between(now, opened.plus(ArenaRules.ANSWER_TIME)).toSeconds());
				question = new QuestionView(index, index + 1, ArenaRules.QUESTIONS, q.getStem(), q.getOptions(), left, mine);
			} else {
				final int idx = index;
				long out = roundEntries.stream().filter(e -> e.survived(idx) >= idx && e.survived(idx + 1) < idx + 1).count();
				reveal = new Reveal(index, q.getStem(), q.getCorrectAnswer(), mine, out);
			}
		}
		List<Standing> standings = new ArrayList<>();
		if (phase == Phase.LIVE || phase == Phase.FINISHED) {
			for (int i = 0; i < Math.min(10, ranked.size()); i++) {
				ArenaEntry e = ranked.get(i);
				standings.add(new Standing(i + 1, e.getUserId(), e.getName(), e.getCountry(), e.survived(shownClosed), e.score()));
			}
		}
		return new RoundView(id(start), phase, start, joinOpens, roundEntries.size(), alive, you, question, reveal, standings,
				phase == Phase.LIVE || phase == Phase.FINISHED ? ends : null);
	}

	/** Publishes every player's result once, after the round is over (reporting gives XP and badges). */
	private void publish(Instant start, List<ArenaEntry> roundEntries, Instant now) {
		if (published.existsById(id(start))) {
			return;
		}
		try {
			published.insert(new ArenaPublished(id(start)));
		}
		catch (DuplicateKeyException alreadyDone) {
			return;
		}
		int last = ArenaRules.lastQuestion(start, roundEntries, now);
		int closed = last + 1;
		List<ArenaEntry> ranked = new ArrayList<>(roundEntries);
		ranked.sort(ArenaRules.ranking(closed));
		ArenaRound round = roundFor(start);
		Instant ended = ArenaRules.endsAt(start, last);
		for (int i = 0; i < ranked.size(); i++) {
			ArenaEntry e = ranked.get(i);
			int survived = e.survived(closed);
			int asked = Math.min(closed, survived + 1);
			List<PlayedQuestion> played = new ArrayList<>();
			for (int q = 0; q < asked; q++) {
				SessionQuestion sq = round.questions().get(q);
				Answer a = e.answer(q);
				played.add(new PlayedQuestion(sq.getQuestionId(), sq.getDifficulty().level(), sq.getStem(), sq.getCorrectAnswer(),
						a == null ? null : a.answer(), a != null && a.correct(), a == null, a == null ? 0 : a.points()));
			}
			int max = ArenaRules.QUESTIONS * (ArenaRules.BASE_POINTS + ArenaRules.SPEED_POINTS);
			events.publishResult(new SinglePlayerResult("arena:" + id(start) + ":" + e.getUserId(), 0, NAME, 0, e.getUserId(),
					e.score(), max, (int) Math.round(100.0 * e.score() / max), survived, asked, ended, played, "ARENA",
					id(start), null, e.getName(), i + 1, ranked.size()));
		}
	}

	ArenaRound roundFor(Instant start) {
		return rounds.findById(id(start)).orElseGet(() -> {
			DailyChallengeService.Picked p = DailyChallengeService.pick(start.getEpochSecond() * 31L + 7, games.findAll(), 4, 4, 4);
			if (p.questions().isEmpty()) {
				throw new QuizRuleException("There are no questions for the arena yet");
			}
			try {
				return rounds.insert(new ArenaRound(id(start), start, p.questions(), p.topics()));
			}
			catch (DuplicateKeyException raced) {
				return rounds.findById(id(start)).orElseThrow();
			}
		});
	}

	static String id(Instant start) {
		return start.toString();
	}

	static String validCountry(String c) {
		return c != null && c.matches("[A-Za-z]{2}") ? c.toUpperCase(java.util.Locale.ROOT) : null;
	}
}
