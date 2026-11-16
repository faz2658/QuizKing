package com.fazeel.quizking.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

/**
 * The browser calls the API from the site's https address, which the service behind the proxy sees as
 * cross-origin, so every HTTP method this service's controllers use must be allowed for that origin.
 */
class WebConfigCorsTest {

	static final String SITE = "https://quiz.example.test";

	static class Registry extends CorsRegistry {
		Map<String, CorsConfiguration> configs() {
			return getCorsConfigurations();
		}
	}

	@Test
	void allowsEveryMethodTheControllersUse() {
		Registry registry = new Registry();
		new WebConfig(new String[] { SITE }).addCorsMappings(registry);
		CorsConfiguration api = registry.configs().get("/api/**");

		assertThat(api.checkOrigin(SITE)).isEqualTo(SITE);
		for (HttpMethod method : new HttpMethod[] { HttpMethod.GET, HttpMethod.POST }) {
			assertThat(api.checkHttpMethod(method)).as(method.name()).isNotNull();
		}
	}
}
