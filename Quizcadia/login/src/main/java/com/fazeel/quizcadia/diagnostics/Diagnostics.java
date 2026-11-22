package com.fazeel.quizcadia.diagnostics;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Who may fetch recordings, heap dumps and thread dumps: only requests from inside the service's own container
 * ({@code docker compose exec <service> curl http://localhost:<port>/actuator/...}, which is what
 * {@code deploy/profile.sh} does). Heap dumps contain secrets, so the Docker network (other containers, Prometheus,
 * Grafana) must not reach them. The services don't trust X-Forwarded-For, so the address can't be faked.
 *
 * <p>A copy of quizcadia-security's, which the login service does not use.
 */
public final class Diagnostics {

	public static final String[] PATHS = { "/actuator/jfr", "/actuator/jfr/**", "/actuator/heapdump",
			"/actuator/threaddump" };

	private Diagnostics() {
	}

	/** Allows a request only from the container itself (loopback), for endpoints that can expose secrets. */
	public static AuthorizationManager<RequestAuthorizationContext> fromInsideTheContainer() {
		return (authentication, context) -> new AuthorizationDecision(isLoopback(context.getRequest().getRemoteAddr()));
	}

	static boolean isLoopback(String address) {
		if (address == null || address.isBlank()) {
			return false;
		}
		try {
			// only literal addresses: getRemoteAddr never gives a name, and a name must not trigger a DNS lookup
			if (!address.matches("[0-9a-fA-F.:%]+")) {
				return false;
			}
			return InetAddress.getByName(address).isLoopbackAddress();
		}
		catch (UnknownHostException e) {
			return false;
		}
	}
}
