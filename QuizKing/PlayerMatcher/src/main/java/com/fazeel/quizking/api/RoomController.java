package com.fazeel.quizking.api;

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

import com.fazeel.quizking.auth.Callers;
import com.fazeel.quizking.matching.PrivateRooms;
import com.fazeel.quizking.matching.PrivateRooms.RoomException;
import com.fazeel.quizking.matching.PrivateRooms.RoomStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/** Private rooms with a join code. The player is always the caller (from the token). */
@RestController
@RequestMapping("/api/v1/matching/rooms")
public class RoomController {

	private final PrivateRooms rooms;

	public RoomController(PrivateRooms rooms) {
		this.rooms = rooms;
	}

	public record CreateRoom(@Positive int gameId) {
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public RoomStatus create(@Valid @RequestBody CreateRoom request) {
		return rooms.create(request.gameId(), Callers.currentUserId(), Callers.currentUserName());
	}

	@GetMapping("/{code}")
	public RoomStatus status(@PathVariable String code) {
		return rooms.status(code);
	}

	@PostMapping("/{code}/players")
	public RoomStatus join(@PathVariable String code) {
		return rooms.join(code, Callers.currentUserId(), Callers.currentUserName());
	}

	@DeleteMapping("/{code}/players")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void leave(@PathVariable String code) {
		rooms.leave(code, Callers.currentUserId());
	}

	@PostMapping("/{code}/start")
	public RoomStatus start(@PathVariable String code) {
		return rooms.start(code, Callers.currentUserId());
	}

	@ExceptionHandler(RoomException.class)
	ProblemDetail roomRule(RoomException e) {
		return ProblemDetail.forStatusAndDetail(e.isNotFound() ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT, e.getMessage());
	}
}
