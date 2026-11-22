package com.fazeel.quizcadia.api;

import java.util.List;
import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fazeel.quizcadia.recommend.Recommendation;
import com.fazeel.quizcadia.recommend.RecommendationService;

import jakarta.validation.constraints.Size;

/** Recommendations and favourite categories; a player can only see and change their own (admins anyone's). */
@RestController
@RequestMapping("/api/v1/recommendation")
public class RecommendationController {

	private final RecommendationService service;

	public RecommendationController(RecommendationService service) {
		this.service = service;
	}

	/** Games ranked for the player, up to limit (1-50). */
	@GetMapping("/users/{userId}")
	public List<Recommendation> forUser(@PathVariable int userId, @RequestParam(defaultValue = "12") int limit) {
		return service.forUser(userId, limit);
	}

	/** The most played games overall, up to limit (1-50). */
	@GetMapping("/trending")
	public List<Recommendation> trending(@RequestParam(defaultValue = "12") int limit) {
		return service.trending(limit);
	}

	/** The category ids the player picked. */
	@GetMapping("/users/{userId}/favorite-categories")
	public List<Integer> favoriteCategories(@PathVariable int userId) {
		return service.favoriteCategories(userId);
	}

	/** Replaces the player's favourite categories (at most 20) and returns them. */
	@PutMapping("/users/{userId}/favorite-categories")
	public List<Integer> setFavoriteCategories(@PathVariable int userId,
			@RequestBody @Size(max = 20) Set<Integer> categoryIds) {
		service.setFavoriteCategories(userId, categoryIds);
		return service.favoriteCategories(userId);
	}
}
