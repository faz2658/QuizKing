package com.fazeel.quizcadia.push;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PushServicesTest {

	@Test
	void acceptsTheRealPushServices() {
		assertThat(PushServices.allowed("https://fcm.googleapis.com/fcm/send/abc:def")).isTrue();
		assertThat(PushServices.allowed("https://web.push.apple.com/QK7f1b2c")).isTrue();
		assertThat(PushServices.allowed("https://updates.push.services.mozilla.com/wpush/v2/gAAAA")).isTrue();
		assertThat(PushServices.allowed("https://wns2-by3p.notify.windows.com/w/?token=x")).isTrue();
	}

	@Test
	void refusesEverythingElseSoTheServerCantBeAimedInside() {
		for (String url : new String[] { "http://fcm.googleapis.com/x", "https://localhost/x", "https://169.254.169.254/latest",
				"https://game-manager:9091/api", "https://fcm.googleapis.com.evil.example/x", "https://evilpush.apple.com/x",
				"https://user@fcm.googleapis.com/x", "https://fcm.googleapis.com:8443/x", "not a url", "", null }) {
			assertThat(PushServices.allowed(url)).as(String.valueOf(url)).isFalse();
		}
	}
}
