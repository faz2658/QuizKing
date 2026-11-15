package com.fazeel.maverick.opentdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.fazeel.maverick.bank.QuestionBankService;
import com.fazeel.maverick.opentdb.ImportStatus.State;

class OpenTriviaImporterTest {

	final QuestionBankService bank = mock(QuestionBankService.class);
	final List<Duration> pauses = new ArrayList<>();

	static TriviaQuestion q(int i, String category) {
		return new TriviaQuestion("multiple", "easy", category, "Question " + i + "?", "A" + i,
				List.of("B" + i, "C" + i, "D" + i));
	}

	static List<TriviaQuestion> qs(int from, int to, String category) {
		List<TriviaQuestion> l = new ArrayList<>();
		for (int i = from; i < to; i++) {
			l.add(q(i, category));
		}
		return l;
	}

	/** Replays scripted responses and records the amounts asked for. */
	static final class ScriptedSource implements TriviaSource {
		final Deque<TriviaBatch> script = new ArrayDeque<>();
		final List<Integer> amounts = new ArrayList<>();
		int tokens;

		@Override
		public String newToken() {
			return "t" + (++tokens);
		}

		@Override
		public TriviaBatch fetch(String token, int amount) {
			amounts.add(amount);
			return script.removeFirst();
		}
	}

	/** Runs submitted work immediately on the calling thread. */
	static final class DirectExecutor extends AbstractExecutorService {
		public void execute(Runnable r) {
			r.run();
		}

		public void shutdown() {
		}

		public List<Runnable> shutdownNow() {
			return List.of();
		}

		public boolean isShutdown() {
			return false;
		}

		public boolean isTerminated() {
			return false;
		}

		public boolean awaitTermination(long t, TimeUnit u) {
			return true;
		}
	}

	OpenTriviaImporter importer(TriviaSource source) {
		return new OpenTriviaImporter(source, new TriviaMapper(new Random(1)), bank, new DirectExecutor(),
				pauses::add, Duration.ofSeconds(5), Clock.systemUTC());
	}

	@Test
	void downloadsEverythingRespectingTheRateLimitAndHandlingTheTail() {
		when(bank.importQuestions(anyString(), anyString(), anyList()))
				.thenAnswer(i -> ((List<?>) i.getArgument(2)).size());
		ScriptedSource src = new ScriptedSource();
		src.script.add(new TriviaBatch(0, qs(0, 50, "Entertainment: Film")));
		src.script.add(new TriviaBatch(5, List.of())); // rate limited once
		src.script.add(new TriviaBatch(0, qs(50, 100, "Geography")));
		src.script.add(new TriviaBatch(1, List.of())); // fewer than 50 left
		src.script.add(new TriviaBatch(0, qs(100, 107, "Geography")));
		src.script.add(new TriviaBatch(4, List.of())); // token exhausted: done

		ImportStatus status = importer(src).start();
		OpenTriviaImporter.class.getName(); // started synchronously by the direct executor

		assertThat(src.amounts).containsExactly(50, 50, 50, 50, 10, 10);
		assertThat(pauses).allMatch(d -> d.equals(Duration.ofSeconds(5))).hasSizeGreaterThanOrEqualTo(6);
		verify(bank).importQuestions(eq("Entertainment"), eq("Film"), org.mockito.ArgumentMatchers.argThat(l -> l.size() == 50));
		verify(bank).importQuestions(eq("Geography"), eq("Geography"), org.mockito.ArgumentMatchers.argThat(l -> l.size() == 57));
		assertThat(status.state()).isEqualTo(State.RUNNING);
	}

	@Test
	void reportsDoneWithCounts() {
		when(bank.importQuestions(anyString(), anyString(), anyList())).thenReturn(3);
		ScriptedSource src = new ScriptedSource();
		src.script.add(new TriviaBatch(0, qs(0, 3, "History")));
		src.script.add(new TriviaBatch(4, List.of()));
		OpenTriviaImporter importer = importer(src);

		importer.start();

		assertThat(importer.status().state()).isEqualTo(State.DONE);
		assertThat(importer.status().fetched()).isEqualTo(3);
		assertThat(importer.status().added()).isEqualTo(3);
		assertThat(importer.status().topics()).isEqualTo(1);
	}

	@Test
	void unknownTokenGetsANewOne() {
		ScriptedSource src = new ScriptedSource();
		src.script.add(new TriviaBatch(3, List.of()));
		src.script.add(new TriviaBatch(4, List.of()));

		importer(src).start();

		assertThat(src.tokens).isEqualTo(2);
	}

	@Test
	void failureKeepsWhatWasDownloaded() {
		when(bank.importQuestions(anyString(), anyString(), anyList())).thenReturn(2);
		TriviaSource src = new TriviaSource() {
			int calls;

			public String newToken() {
				return "t";
			}

			public TriviaBatch fetch(String token, int amount) {
				if (calls++ == 0) {
					return new TriviaBatch(0, qs(0, 2, "Sports"));
				}
				throw new IllegalStateException("network down");
			}
		};
		OpenTriviaImporter importer = importer(src);

		importer.start();

		assertThat(importer.status().state()).isEqualTo(State.FAILED);
		assertThat(importer.status().added()).isEqualTo(2);
		verify(bank).importQuestions(eq("Sports"), eq("Sports"), anyList());
	}
}
