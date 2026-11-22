package com.fazeel.quizcadia.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fazeel.quizcadia.arena.ArenaService;
import com.fazeel.quizcadia.arena.ArenaService.ArenaView;
import com.fazeel.quizcadia.auth.Callers;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** The World Arena. Players poll GET about once a second; all timing is server time. */
@RestController
@RequestMapping("/api/v1/arena")
public class ArenaController {

	private final ArenaService arena;

	public ArenaController(ArenaService arena) {
		this.arena = arena;
	}

	/** The player's country (ISO code), optional; kept only when it is two letters. */
	public record JoinRequest(String country) {
	}

	/** The answer to question questionIndex (0-based) of the running round. */
	public record ArenaAnswer(@Min(0) int questionIndex, @NotNull String answer) {
	}

	/** The running or next round and the one before it, as the caller sees them. */
	@GetMapping
	public ArenaView view() {
		return arena.view(Callers.currentUserId());
	}

	/** Joins the next round (opens 10 minutes before it starts). */
	@PostMapping("/join")
	public ArenaView join(@RequestBody(required = false) JoinRequest body) {
		return arena.join(Callers.currentUserId(), Callers.currentUserName(), body == null ? null : body.country());
	}

	/** Answers the open question; a wrong answer puts the player out. */
	@PostMapping("/answers")
	public ArenaView answer(@Valid @RequestBody ArenaAnswer body) {
		return arena.answer(Callers.currentUserId(), body.questionIndex(), body.answer());
	}
}
