package com.fazeel.quizcadia.push;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Streak reminders by Web Push, switched on by a VAPID key pair (QUIZCADIA_PUSH_PUBLIC_KEY and _PRIVATE_KEY; see
 * deploy/README.md for making one). Without it the app hides the "Remind me" button and nothing is sent.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class PushConfig {

	@Bean
	@ConditionalOnExpression("!'${quizcadia.push.private-key:}'.isBlank() and !'${quizcadia.push.public-key:}'.isBlank()")
	WebPush webPush(@Value("${quizcadia.push.public-key}") String publicKey,
			@Value("${quizcadia.push.private-key}") String privateKey,
			@Value("${quizcadia.push.subject:mailto:support@quizcadia.com}") String subject) {
		return new WebPush(publicKey.trim(), privateKey.trim(), subject.trim(),
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build());
	}
}
