package com.fazeel.quizcadia.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fazeel.quizcadia.domain.Match;
import com.fazeel.quizcadia.matching.PrivateRooms.RoomException;
import com.fazeel.quizcadia.matching.PrivateRooms.RoomStatus;
import com.fazeel.quizcadia.matching.PrivateRooms.State;

class PrivateRoomsTest {

	List<Match> started = new ArrayList<>();
	PrivateRooms rooms;

	@BeforeEach
	void setUp() {
		rooms = new PrivateRooms(Clock.fixed(Instant.parse("2026-11-15T12:00:00Z"), ZoneOffset.UTC), new Random(4), started::add);
	}

	@Test
	void hostCreatesFriendsJoinWithTheCodeAndTheHostStarts() {
		RoomStatus room = rooms.create(7, 1, "Ada");
		assertThat(room.code()).matches("KING-\\d{4}");
		assertThat(room.state()).isEqualTo(State.WAITING);

		rooms.join(room.code().toLowerCase(), 2, "Bo"); // codes aren't case sensitive
		RoomStatus three = rooms.join(room.code().substring(5), 3, "Cy"); // the digits alone work too
		assertThat(three.players()).extracting(PrivateRooms.Player::name).containsExactly("Ada", "Bo", "Cy");

		RoomStatus go = rooms.start(room.code(), 1);
		assertThat(go.state()).isEqualTo(State.STARTED);
		assertThat(started).singleElement().satisfies(m -> {
			assertThat(m.gameId()).isEqualTo(7);
			assertThat(m.players()).containsExactly(1, 2, 3);
			assertThat(m.matchId()).isEqualTo(go.matchId());
		});
		assertThat(rooms.status(room.code()).matchId()).isEqualTo(go.matchId()); // everyone polling sees it
	}

	@Test
	void onlyTheHostStartsAndNotAlone() {
		RoomStatus room = rooms.create(7, 1, "Ada");
		assertThatThrownBy(() -> rooms.start(room.code(), 1)).isInstanceOf(RoomException.class).hasMessageContaining("friend");
		rooms.join(room.code(), 2, "Bo");
		assertThatThrownBy(() -> rooms.start(room.code(), 2)).hasMessageContaining("Only the host");
		assertThat(started).isEmpty();
	}

	@Test
	void startedOrFullRoomsTurnNewcomersAway() {
		RoomStatus room = rooms.create(7, 1, "Ada");
		for (int id = 2; id <= PrivateRooms.MAX_PLAYERS; id++) {
			rooms.join(room.code(), id, "P" + id);
		}
		assertThatThrownBy(() -> rooms.join(room.code(), 99, "Late")).hasMessageContaining("full");
		rooms.start(room.code(), 1);
		assertThat(rooms.join(room.code(), 2, "P2").state()).isEqualTo(State.STARTED); // members may re-check
		assertThatThrownBy(() -> rooms.join(room.code(), 100, "Later")).hasMessageContaining("started");
	}

	@Test
	void theRoomClosesWhenTheHostLeaves() {
		RoomStatus room = rooms.create(7, 1, "Ada");
		rooms.join(room.code(), 2, "Bo");
		rooms.leave(room.code(), 2);
		assertThat(rooms.status(room.code()).players()).hasSize(1);
		rooms.leave(room.code(), 1);
		assertThat(rooms.status(room.code()).state()).isEqualTo(State.CLOSED);
	}

	@Test
	void unknownCodesAreNotFound() {
		assertThatThrownBy(() -> rooms.join("KING-0000", 1, "Ada")).isInstanceOfSatisfying(RoomException.class,
				e -> assertThat(e.isNotFound()).isTrue());
	}

	@Test
	void aPartyHostIsTheScreenNotAPlayer() {
		RoomStatus room = rooms.create(7, 1, "Teacher", true);
		assertThat(room.party()).isTrue();
		assertThat(room.players()).isEmpty();
		assertThat(room.maxPlayers()).isEqualTo(PrivateRooms.PARTY_MAX_PLAYERS);

		assertThat(rooms.join(room.code(), 1, "Teacher").players()).isEmpty(); // the screen never becomes a player
		rooms.join(room.code(), 2, "Bo");
		assertThatThrownBy(() -> rooms.start(room.code(), 1)).hasMessageContaining("two players");
		for (int id = 3; id <= 31; id++) {
			rooms.join(room.code(), id, "P" + id);
		}
		assertThatThrownBy(() -> rooms.join(room.code(), 99, "Late")).hasMessageContaining("full (30 players)");

		rooms.start(room.code(), 1);
		assertThat(started).singleElement().satisfies(m -> {
			assertThat(m.players()).hasSize(30).doesNotContain(1);
			assertThat(m.screenId()).isEqualTo(1);
			assertThat(m.names()).containsEntry(2, "Bo");
		});
	}

	@Test
	void aQuizRoomIsAPartyWithoutAGameAndOnePlayerIsEnough() {
		RoomStatus room = rooms.createQuiz(1, "Host", false);
		assertThat(room.quiz()).isTrue();
		assertThat(room.party()).isTrue();
		assertThat(room.players()).isEmpty(); // the host is the big screen
		assertThat(room.maxPlayers()).isEqualTo(PrivateRooms.QUIZ_MAX_PLAYERS);
		assertThatThrownBy(() -> rooms.start(room.code(), 1)).isInstanceOf(RoomException.class);

		rooms.join(room.code(), 900_000_001, "Phone");
		RoomStatus go = rooms.start(room.code(), 1);

		assertThat(go.state()).isEqualTo(State.STARTED);
		assertThat(started).singleElement().satisfies(m -> {
			assertThat(m.gameId()).isZero();
			assertThat(m.players()).containsExactly(900_000_001);
			assertThat(m.screenId()).isEqualTo(1);
			assertThat(m.names()).containsEntry(900_000_001, "Phone");
			assertThat(m.quiz()).isEqualTo(new Match.RoomQuiz(20, 20, 0));
		});
	}

	@Test
	void aQuizRoomCanMoveOnByItself() {
		RoomStatus room = rooms.createQuiz(1, "Host", true);
		rooms.join(room.code(), 2, "Bo");
		rooms.start(room.code(), 1);

		assertThat(started.get(0).quiz().revealSeconds()).isEqualTo(PrivateRooms.QUIZ_AUTO_REVEAL_SECONDS);
	}

	@Test
	void ordinaryRoomsCarryNoQuiz() {
		RoomStatus room = rooms.create(7, 1, "Ada", true);
		rooms.join(room.code(), 2, "Bo");
		rooms.join(room.code(), 3, "Cy");
		rooms.start(room.code(), 1);

		assertThat(room.quiz()).isFalse();
		assertThat(started.get(0).quiz()).isNull();
	}
}
