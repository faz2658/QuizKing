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

@RestController
@RequestMapping("/api/v1/recommendation")
public class RecommendationController {

	private final RecommendationService service;

	public RecommendationController(RecommendationService service) {
		this.service = service;
	}

	@GetMapping("/users/{userId}")
	public List<Recommendation> forUser(@PathVariable int userId, @RequestParam(defaultValue = "12") int limit) {
		return service.forUser(userId, limit);
	}

	@GetMapping("/trending")
	public List<Recommendation> trending(@RequestParam(defaultValue = "12") int limit) {
		return service.trending(limit);
	}

	@GetMapping("/users/{userId}/favorite-categories")
	public List<Integer> favoriteCategories(@PathVariable int userId) {
		return service.favoriteCategories(userId);
	}

	@PutMapping("/users/{userId}/favorite-categories")
	public List<Integer> setFavoriteCategories(@PathVariable int userId,
			@RequestBody @Size(max = 20) Set<Integer> categoryIds) {
		service.setFavoriteCategories(userId, categoryIds);
		return service.favoriteCategories(userId);
	}
}
