package com.fazeel.quizcadia.opentdb;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fazeel.quizcadia.bank.QuestionBankService;
import com.fazeel.quizcadia.domain.Questions;
import com.fazeel.quizcadia.opentdb.ImportStatus.State;
import com.fazeel.quizcadia.opentdb.TriviaMapper.Placement;

/**
 * Downloads the whole Open Trivia DB (about 5,000 verified questions) into the question bank. It uses
 * a session token so no question is fetched twice, respects the one-request-per-5-seconds limit, and
 * runs in the background; {@link #status()} reports progress. Questions are saved per topic every few
 * hundred questions, so games become playable while the import is still running.
 */
public class OpenTriviaImporter {

	private static final Logger log = LoggerFactory.getLogger(OpenTriviaImporter.class);
	static final int BATCH = 50;
	static final int FLUSH_EVERY = 500;

	/** Pauses between requests; replaced in tests. */
	public interface Pause {
		void sleep(Duration d) throws InterruptedException;
	}

	private final TriviaSource source;
	private final TriviaMapper mapper;
	private final QuestionBankService bank;
	private final ExecutorService executor;
	private final Pause pause;
	private final Duration spacing;
	private final Clock clock;
	private final AtomicReference<ImportStatus> status = new AtomicReference<>(ImportStatus.idle());

	public OpenTriviaImporter(TriviaSource source, TriviaMapper mapper, QuestionBankService bank,
			ExecutorService executor, Pause pause, Duration spacing, Clock clock) {
		this.source = source;
		this.mapper = mapper;
		this.bank = bank;
		this.executor = executor;
		this.pause = pause;
		this.spacing = spacing;
		this.clock = clock;
	}

	/** The current or last import's progress. */
	public ImportStatus status() {
		return status.get();
	}

	/** Starts an import unless one is running. @return the current status */
	public ImportStatus start() {
		ImportStatus current = status.get();
		if (current.state() == State.RUNNING) {
			return current;
		}
		ImportStatus running = new ImportStatus(State.RUNNING, 0, 0, 0, clock.instant(), null, "Starting");
		if (!status.compareAndSet(current, running)) {
			return status.get();
		}
		executor.submit(this::run);
		return running;
	}

	void run() {
		Map<Placement, List<Questions>> pending = new LinkedHashMap<>();
		Map<Placement, Boolean> seenTopics = new LinkedHashMap<>();
		int fetched = 0;
		int added = 0;
		int amount = BATCH;
		int buffered = 0;
		try {
			String token = source.newToken();
			while (true) {
				pause.sleep(spacing);
				TriviaBatch batch = source.fetch(token, amount);
				switch (batch.responseCode()) {
					case TriviaBatch.OK -> {
						for (TriviaQuestion t : batch.questions()) {
							Placement p = mapper.placement(t);
							mapper.map(t).ifPresent(q -> pending.computeIfAbsent(p, k -> new ArrayList<>()).add(q));
							seenTopics.put(p, true);
						}
						fetched += batch.questions().size();
						buffered += batch.questions().size();
					}
					case TriviaBatch.NO_RESULTS -> {
						if (amount == 1) {
							break; // nothing left
						}
						amount = Math.max(1, amount / 5); // fewer than 50 left: ask for less
						continue;
					}
					case TriviaBatch.RATE_LIMITED -> {
						pause.sleep(spacing);
						continue;
					}
					case TriviaBatch.TOKEN_NOT_FOUND -> {
						token = source.newToken();
						continue;
					}
					case TriviaBatch.TOKEN_EMPTY -> {
						// every question has been delivered
					}
					default -> throw new IllegalStateException("Open Trivia DB response code " + batch.responseCode());
				}
				if (batch.responseCode() == TriviaBatch.TOKEN_EMPTY
						|| (batch.responseCode() == TriviaBatch.NO_RESULTS && amount == 1)) {
					break;
				}
				if (buffered >= FLUSH_EVERY) {
					added += flush(pending);
					buffered = 0;
				}
				status.set(new ImportStatus(State.RUNNING, fetched, added, seenTopics.size(), status.get().startedAt(),
						null, "Downloading questions"));
			}
			added += flush(pending);
			status.set(new ImportStatus(State.DONE, fetched, added, seenTopics.size(), status.get().startedAt(),
					clock.instant(), "Imported " + added + " new questions in " + seenTopics.size() + " topics"));
			log.info("Open Trivia DB import done: {} fetched, {} new, {} topics", fetched, added, seenTopics.size());
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			fail(fetched, added, seenTopics.size(), "Interrupted");
		}
		catch (RuntimeException e) {
			log.warn("Open Trivia DB import failed", e);
			try {
				added += flush(pending); // keep what was downloaded
			}
			catch (RuntimeException ignored) {
				// already failing
			}
			fail(fetched, added, seenTopics.size(), e.getMessage());
		}
	}

	private int flush(Map<Placement, List<Questions>> pending) {
		int added = 0;
		for (Map.Entry<Placement, List<Questions>> e : pending.entrySet()) {
			added += bank.importQuestions(e.getKey().category(), e.getKey().topic(), e.getValue());
		}
		pending.clear();
		return added;
	}

	private void fail(int fetched, int added, int topics, String message) {
		status.set(new ImportStatus(State.FAILED, fetched, added, topics, status.get().startedAt(), clock.instant(),
				message));
	}
}
