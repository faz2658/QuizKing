package com.fazeel.maverick.search;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

	private final SearchService service;

	public SearchController(SearchService service) {
		this.service = service;
	}

	@GetMapping
	public List<GameCard> search(@RequestParam("q") String query) {
		return service.search(query);
	}
}
