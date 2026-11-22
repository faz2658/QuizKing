package com.fazeel.quizcadia.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Lets the site's own origins call this API from the browser (CORS); the allowed origins come from configuration. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

	private final String[] allowedOrigins;

	public WebConfig(@Value("${quizcadia.cors.allowed-origins}") String[] allowedOrigins) {
		this.allowedOrigins = allowedOrigins;
	}

	/** Allows GET, POST and DELETE on /api/** from the configured origins. */
	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**").allowedOrigins(allowedOrigins).allowedMethods("GET", "POST", "DELETE");
	}
}
