package com.fazeel.quizcadia.api;

import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

import jakarta.servlet.http.HttpServletRequest;

/**
 * ETags for the public catalog lists ({@code /api/game/categories}, {@code /countries}, {@code /games},
 * {@code /games/mp}): a browser or CDN that already has the list sends {@code If-None-Match} and gets an empty
 * "304 not modified" instead of the whole list again. Only those lists: everything else stays as it was.
 */
@Component
public class CatalogEtags extends ShallowEtagHeaderFilter {

	static final Set<String> LISTS = Set.of("/api/game/categories", "/api/game/countries",
			"/api/game/games", "/api/game/games/mp");

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !"GET".equals(request.getMethod()) || !LISTS.contains(request.getRequestURI());
	}
}
