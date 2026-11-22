package com.fazeel.quizcadia.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fazeel.quizcadia.auth.Callers;
import com.fazeel.quizcadia.matching.PrivateRooms;
import com.fazeel.quizcadia.matching.PrivateRooms.RoomException;
import com.fazeel.quizcadia.matching.PrivateRooms.RoomStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.PositiveOrZero;

/** Private rooms with a join code. The player is always the caller (from the token). */
@RestController
@RequestMapping("/api/v1/matching/rooms")
public class RoomController {

	private final PrivateRooms rooms;

	public RoomController(PrivateRooms rooms) {
		this.rooms = rooms;
	}

	/**
	 * {@code party}: a big-screen party where the host shows the game and doesn't play. {@code quiz}: a live room
	 * quiz instead of a game (no {@code gameId}); {@code autoAdvance} moves on from each answer by itself.
	 */
	public record CreateRoom(@PositiveOrZero int gameId, boolean party, boolean quiz, boolean autoAdvance) {

		@AssertTrue(message = "Pick a game")
		public boolean isGameChosen() {
			return quiz || gameId > 0;
		}
	}

	/** Opens a room hosted by the caller: a private room, a party room for a big screen, or a live room quiz. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public RoomStatus create(@Valid @RequestBody CreateRoom request) {
		if (request.quiz()) {
			return rooms.createQuiz(Callers.currentUserId(), Callers.currentUserName(), request.autoAdvance());
		}
		return rooms.create(request.gameId(), Callers.currentUserId(), Callers.currentUserName(), request.party());
	}

	/** The room with this code (anyone signed in may look before joining). */
	@GetMapping("/{code}")
	public RoomStatus status(@PathVariable String code) {
		return rooms.status(code);
	}

	/** Adds the caller to the room (joining twice changes nothing; the party host never becomes a player). */
	@PostMapping("/{code}/players")
	public RoomStatus join(@PathVariable String code) {
		return rooms.join(code, Callers.currentUserId(), Callers.currentUserName());
	}

	/** Takes the caller out of a waiting room; when the host leaves, the room closes. */
	@DeleteMapping("/{code}/players")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void leave(@PathVariable String code) {
		rooms.leave(code, Callers.currentUserId());
	}

	/** Starts the room's match (host only). */
	@PostMapping("/{code}/start")
	public RoomStatus start(@PathVariable String code) {
		return rooms.start(code, Callers.currentUserId());
	}

	@ExceptionHandler(RoomException.class)
	ProblemDetail roomRule(RoomException e) {
		return ProblemDetail.forStatusAndDetail(e.isNotFound() ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT, e.getMessage());
	}
}
