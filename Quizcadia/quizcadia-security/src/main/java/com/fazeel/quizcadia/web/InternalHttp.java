package com.fazeel.quizcadia.web;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * HTTP clients with time limits. Without them a service that hangs (rather than refusing the connection) holds the
 * caller's request thread for good, and the player's spinner with it. Failures then surface as
 * {@link org.springframework.web.client.ResourceAccessException}, which the callers already treat as "unavailable".
 */
public final class InternalHttp {

	/** Inside the Docker network a connection takes milliseconds; anything slower means the service is not there. */
	public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
	/** Long enough for the biggest topic pool, short enough that nginx (60 s) never gives up first. */
	public static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

	private InternalHttp() {
	}

	/** A request factory with the default time limits. */
	public static ClientHttpRequestFactory requestFactory() {
		return requestFactory(READ_TIMEOUT);
	}

	/** A request factory with the connect limit and the given read limit. */
	public static ClientHttpRequestFactory requestFactory(Duration readTimeout) {
		HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
		factory.setReadTimeout(readTimeout);
		return factory;
	}

	/** A client for another QuizCadia service (or any other HTTP API) with the default time limits. */
	public static RestClient.Builder client(String baseUrl) {
		return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory());
	}
}
