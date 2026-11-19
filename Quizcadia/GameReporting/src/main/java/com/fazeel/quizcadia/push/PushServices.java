package com.fazeel.quizcadia.push;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * The push services browsers use. A subscription is a URL the browser hands us and our server then posts to, so
 * anything else is refused: otherwise anyone could make the server call internal addresses (SSRF).
 */
public final class PushServices {

	/** Exact hosts, and parent domains (a host ending in "." + one of these). */
	private static final Set<String> HOSTS = Set.of("fcm.googleapis.com", "updates.push.services.mozilla.com");
	private static final Set<String> DOMAINS = Set.of("push.apple.com", "notify.windows.com");

	private PushServices() {
	}

	public static boolean allowed(String endpoint) {
		if (endpoint == null || endpoint.length() > 2048) {
			return false;
		}
		try {
			URI uri = new URI(endpoint);
			if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
					|| (uri.getPort() != -1 && uri.getPort() != 443)) {
				return false;
			}
			String host = uri.getHost().toLowerCase(Locale.ROOT);
			return HOSTS.contains(host) || DOMAINS.stream().anyMatch(d -> host.equals(d) || host.endsWith("." + d));
		}
		catch (java.net.URISyntaxException e) {
			return false;
		}
	}
}
