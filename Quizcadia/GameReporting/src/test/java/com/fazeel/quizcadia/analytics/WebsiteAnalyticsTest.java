package com.fazeel.quizcadia.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fazeel.quizcadia.board.BoardCache;

import tools.jackson.databind.ObjectMapper;

class WebsiteAnalyticsTest {

	static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-16T04:00:00Z"), ZoneOffset.UTC);
	static final String RUN = "https://analyticsdata.googleapis.com/v1beta/properties/564461039:runReport";
	static KeyPair keys;
	static String keyJson;

	@BeforeAll
	static void serviceAccount() throws Exception {
		KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
		g.initialize(2048);
		keys = g.generateKeyPair();
		String pem = "-----BEGIN PRIVATE KEY-----\n"
				+ Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keys.getPrivate().getEncoded())
				+ "\n-----END PRIVATE KEY-----\n";
		keyJson = new ObjectMapper().writeValueAsString(java.util.Map.of("type", "service_account", "client_email",
				"quiz-king-analytics@quiz-king-514821.iam.gserviceaccount.com", "private_key", pem, "token_uri",
				"https://oauth2.googleapis.com/token"));
	}

	static String rows(String... dimMetric) {
		StringBuilder b = new StringBuilder("{\"rows\":[");
		for (int i = 0; i < dimMetric.length; i += 2) {
			if (i > 0) b.append(',');
			b.append("{\"dimensionValues\":[{\"value\":\"").append(dimMetric[i]).append("\"}],\"metricValues\":[{\"value\":\"")
					.append(dimMetric[i + 1]).append("\"}]}");
		}
		return b.append("]}").toString();
	}

	static void expectToken(MockRestServiceServer server) {
		server.expect(requestTo("https://oauth2.googleapis.com/token")).andExpect(method(HttpMethod.POST))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=urn")))
				.andRespond(withSuccess("{\"access_token\":\"ya29.test\",\"expires_in\":3599}", MediaType.APPLICATION_JSON));
	}

	@Test
	void theTokenRequestIsSignedWithTheServiceAccountKey() throws Exception {
		GoogleCredentials c = new GoogleCredentials(keyJson, RestClient.create(), CLOCK);
		String[] jwt = c.signedJwt(CLOCK.instant()).split("\\.");
		Signature s = Signature.getInstance("SHA256withRSA");
		s.initVerify(keys.getPublic());
		s.update((jwt[0] + "." + jwt[1]).getBytes(StandardCharsets.US_ASCII));
		assertThat(s.verify(Base64.getUrlDecoder().decode(jwt[2]))).isTrue();
		String claims = new String(Base64.getUrlDecoder().decode(jwt[1]), StandardCharsets.UTF_8);
		assertThat(claims).contains("analytics.readonly").contains("quiz-king-analytics@").contains("oauth2.googleapis.com/token");
	}

	@Test
	void buildsTheAdminViewFromGoogleAndKeepsItForFiveMinutes() {
		RestClient.Builder b = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
		expectToken(server);
		server.expect(requestTo(RUN)).andExpect(header("Authorization", "Bearer ya29.test"))
				.andRespond(withSuccess("{\"rows\":[{\"metricValues\":[{\"value\":\"120\"},{\"value\":\"90\"},{\"value\":\"200\"},{\"value\":\"1500\"},{\"value\":\"185.5\"},{\"value\":\"0.62\"}]}]}", MediaType.APPLICATION_JSON));
		server.expect(requestTo(RUN)).andRespond(withSuccess("{\"rows\":[{\"dimensionValues\":[{\"value\":\"20261115\"}],\"metricValues\":[{\"value\":\"40\"},{\"value\":\"500\"}]},{\"dimensionValues\":[{\"value\":\"20261116\"}],\"metricValues\":[{\"value\":\"80\"},{\"value\":\"1000\"}]}]}", MediaType.APPLICATION_JSON));
		server.expect(requestTo(RUN)).andRespond(withSuccess(rows("/", "700", "/daily", "300"), MediaType.APPLICATION_JSON));
		server.expect(requestTo(RUN)).andRespond(withSuccess(rows("Canada", "70", "India", "30"), MediaType.APPLICATION_JSON));
		server.expect(requestTo(RUN)).andRespond(withSuccess(rows("mobile", "80", "desktop", "40"), MediaType.APPLICATION_JSON));
		server.expect(requestTo(RUN)).andRespond(withSuccess(rows("Direct", "150", "Organic Social", "50"), MediaType.APPLICATION_JSON));
		server.expect(requestTo(RUN)).andRespond(withSuccess(rows("page_view", "1500", "share", "12"), MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://analyticsdata.googleapis.com/v1beta/properties/564461039:runRealtimeReport"))
				.andRespond(withSuccess("{\"rows\":[{\"metricValues\":[{\"value\":\"3\"}]}]}", MediaType.APPLICATION_JSON));

		WebsiteAnalytics ga = new WebsiteAnalytics("564461039", keyJson, b.build(), new BoardCache(CLOCK), CLOCK);
		WebsiteAnalytics.View v = ga.view(28);
		WebsiteAnalytics.View again = ga.view(28); // served from the cache: no more requests expected

		server.verify();
		assertThat(again).isSameAs(v);
		assertThat(v.configured()).isTrue();
		assertThat(v.error()).isNull();
		assertThat(v.activeNow()).isEqualTo(3);
		assertThat(v.totals().activeUsers()).isEqualTo(120);
		assertThat(v.totals().pageViews()).isEqualTo(1500);
		assertThat(v.totals().engagementRate()).isEqualTo(0.62);
		assertThat(v.byDay()).extracting(WebsiteAnalytics.Day::day).containsExactly(LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 16));
		assertThat(v.pages().get(0)).isEqualTo(new WebsiteAnalytics.Row("/", 700));
		assertThat(v.countries()).extracting(WebsiteAnalytics.Row::name).containsExactly("Canada", "India");
		assertThat(v.channels().get(0).value()).isEqualTo(150);
	}

	@Test
	void explainsWhatIsMissingWhenNotSetUp() {
		WebsiteAnalytics noId = new WebsiteAnalytics("", keyJson, RestClient.create(), new BoardCache(CLOCK), CLOCK);
		assertThat(noId.view(28).configured()).isFalse();
		assertThat(noId.view(28).error()).contains("GA_PROPERTY_ID");

		WebsiteAnalytics noKey = new WebsiteAnalytics("564461039", null, RestClient.create(), new BoardCache(CLOCK), CLOCK);
		assertThat(noKey.view(28).error()).contains("GA_CREDENTIALS_FILE");

		WebsiteAnalytics badKey = new WebsiteAnalytics("564461039", "{\"client_email\":\"x\"}", RestClient.create(), new BoardCache(CLOCK), CLOCK);
		assertThat(badKey.view(28).error()).contains("can't be used");
	}

	@Test
	void aRefusedPropertyTellsWhichAccountToAdd() {
		RestClient.Builder b = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
		expectToken(server);
		server.expect(requestTo(RUN)).andRespond(withStatus(HttpStatus.FORBIDDEN).body("{\"error\":{\"code\":403}}"));
		WebsiteAnalytics ga = new WebsiteAnalytics("564461039", keyJson, b.build(), new BoardCache(CLOCK), CLOCK);

		assertThatThrownBy(() -> ga.view(7)).isInstanceOf(WebsiteAnalytics.AnalyticsUnavailable.class)
				.hasMessageContaining("quiz-king-analytics@quiz-king-514821.iam.gserviceaccount.com")
				.hasMessageContaining("Viewer");
	}
}
