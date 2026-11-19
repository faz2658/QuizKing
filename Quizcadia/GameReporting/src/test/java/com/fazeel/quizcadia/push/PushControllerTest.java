package com.fazeel.quizcadia.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.fazeel.quizcadia.config.SecurityConfig;

@Import({ SecurityConfig.class, PushControllerTest.Keys.class })
@WebMvcTest(PushController.class)
class PushControllerTest {

	static final String PUB = "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";

	@TestConfiguration
	static class Keys {
		@Bean
		WebPush webPush() {
			return new WebPush(PUB, "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw", "mailto:x@quizcadia.test", null);
		}
	}

	@Autowired
	MockMvc mvc;

	@MockitoBean
	PushSubscriptionRepository repo;

	static RequestPostProcessor player(int id) {
		return jwt().jwt(j -> j.subject(String.valueOf(id))).authorities(new SimpleGrantedAuthority("ROLE_USER"));
	}

	static String body(String endpoint, String zone) {
		return "{\"endpoint\":\"" + endpoint + "\",\"p256dh\":\"BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4\","
				+ "\"auth\":\"BTBZMqHH6r4Tts7J_aSIgg\",\"timeZone\":\"" + zone + "\"}";
	}

	@Test
	void givesSignedInPlayersThePublicKey() throws Exception {
		mvc.perform(get("/api/v1/reports/push/key").with(player(5))).andExpect(status().isOk())
				.andExpect(jsonPath("$.publicKey").value(PUB));
		mvc.perform(get("/api/v1/reports/push/key")).andExpect(status().isUnauthorized());
	}

	@Test
	void savesTheCallersSubscriptionWithTheirTimeZone() throws Exception {
		when(repo.findById(any())).thenReturn(Optional.empty());

		mvc.perform(post("/api/v1/reports/push/subscriptions").with(player(5)).contentType(MediaType.APPLICATION_JSON)
				.content(body("https://fcm.googleapis.com/fcm/send/abc", "America/Toronto"))).andExpect(status().isNoContent());

		ArgumentCaptor<PushSubscription> s = ArgumentCaptor.forClass(PushSubscription.class);
		verify(repo).save(s.capture());
		assertThat(s.getValue().userId()).isEqualTo(5);
		assertThat(s.getValue().timeZone()).isEqualTo("America/Toronto");
		assertThat(s.getValue().lastRemindedDay()).isNull();
	}

	@Test
	void refusesEndpointsThatArentPushServices() throws Exception {
		mvc.perform(post("/api/v1/reports/push/subscriptions").with(player(5)).contentType(MediaType.APPLICATION_JSON)
				.content(body("https://169.254.169.254/latest/meta-data", "UTC"))).andExpect(status().isBadRequest());
		verify(repo, never()).save(any());
	}

	@Test
	void refusesBrokenKeys() throws Exception {
		mvc.perform(post("/api/v1/reports/push/subscriptions").with(player(5)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"endpoint\":\"https://fcm.googleapis.com/x\",\"p256dh\":\"short\",\"auth\":\"x\"}"))
				.andExpect(status().isBadRequest());
		verify(repo, never()).save(any());
	}

	@Test
	void playersCanOnlyRemoveTheirOwnBrowsers() throws Exception {
		when(repo.findById("https://fcm.googleapis.com/x"))
				.thenReturn(Optional.of(new PushSubscription("https://fcm.googleapis.com/x", 9, "p", "a", "UTC", null, null)));

		mvc.perform(delete("/api/v1/reports/push/subscriptions").with(player(5)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"endpoint\":\"https://fcm.googleapis.com/x\"}")).andExpect(status().isNoContent());
		verify(repo, never()).deleteById(any());

		mvc.perform(delete("/api/v1/reports/push/subscriptions").with(player(9)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"endpoint\":\"https://fcm.googleapis.com/x\"}")).andExpect(status().isNoContent());
		verify(repo).deleteById("https://fcm.googleapis.com/x");
	}

	@Test
	void limitsDevicesPerPlayer() throws Exception {
		when(repo.findById(any())).thenReturn(Optional.empty());
		when(repo.countByUserId(5)).thenReturn((long) PushController.MAX_PER_PLAYER);

		mvc.perform(post("/api/v1/reports/push/subscriptions").with(player(5)).contentType(MediaType.APPLICATION_JSON)
				.content(body("https://fcm.googleapis.com/fcm/send/new", "UTC"))).andExpect(status().isConflict());
	}
}
