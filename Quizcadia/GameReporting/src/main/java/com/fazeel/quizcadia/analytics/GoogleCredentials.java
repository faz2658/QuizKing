package com.fazeel.quizcadia.analytics;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * A Google service account, read from the JSON key file Google Cloud gives you. Trades a signed token (JWT, RS256)
 * for a short-lived read-only access token and reuses it until a minute before it expires. No Google SDK needed.
 */
public class GoogleCredentials {

	static final String SCOPE = "https://www.googleapis.com/auth/analytics.readonly";

	private final String clientEmail;
	private final PrivateKey key;
	private final String tokenUri;
	private final RestClient http;
	private final Clock clock;
	private final ObjectMapper json = new ObjectMapper();
	private String token;
	private Instant expires = Instant.EPOCH;

	public GoogleCredentials(String keyFileJson, RestClient http, Clock clock) {
		JsonNode k = json.readTree(keyFileJson);
		this.clientEmail = k.path("client_email").asString("");
		this.tokenUri = k.path("token_uri").asString("https://oauth2.googleapis.com/token");
		String pem = k.path("private_key").asString("");
		if (clientEmail.isBlank() || pem.isBlank()) {
			throw new IllegalArgumentException("Not a service account key: client_email or private_key is missing");
		}
		this.key = parse(pem);
		this.http = http;
		this.clock = clock;
	}

	/**
	 * The service account's email, shown in the setup instructions (it must be given read access to the property).
	 */
	public String clientEmail() {
		return clientEmail;
	}

	/** A valid access token, fetching a new one when the cached one is about to expire. */
	public synchronized String accessToken() {
		Instant now = clock.instant();
		if (token != null && now.isBefore(expires.minusSeconds(60))) {
			return token;
		}
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
		form.add("assertion", signedJwt(now));
		String body = http.post().uri(tokenUri).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
				.retrieve().body(String.class);
		JsonNode r = json.readTree(body);
		token = r.path("access_token").asString(null);
		if (token == null) {
			throw new IllegalStateException("Google didn't return an access token");
		}
		expires = now.plusSeconds(r.path("expires_in").asLong(3600));
		return token;
	}

	String signedJwt(Instant now) {
		String header = b64("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
		String claims = b64(json.writeValueAsString(Map.of("iss", clientEmail, "scope", SCOPE, "aud",
				tokenUri, "iat", now.getEpochSecond(), "exp", now.getEpochSecond() + 3600)));
		try {
			Signature s = Signature.getInstance("SHA256withRSA");
			s.initSign(key);
			s.update((header + "." + claims).getBytes(StandardCharsets.US_ASCII));
			return header + "." + claims + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(s.sign());
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("Couldn't sign the Google token request", e);
		}
	}

	private static String b64(String s) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
	}

	private static PrivateKey parse(String pem) {
		String b64 = pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "")
				.replaceAll("\\s", "");
		try {
			return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(b64)));
		}
		catch (GeneralSecurityException | IllegalArgumentException e) {
			throw new IllegalArgumentException("The service account's private_key isn't a valid RSA key", e);
		}
	}
}
