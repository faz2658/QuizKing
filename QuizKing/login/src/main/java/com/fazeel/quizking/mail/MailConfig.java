package com.fazeel.quizking.mail;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Real SMTP when MAIL_HOST is set (Oracle Email Delivery, Gmail, Brevo, ... all speak SMTP). Without it the
 * emails are written to the log instead, so a reset link can still be passed on by whoever runs the server.
 */
@Configuration
public class MailConfig {

	private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

	@Bean(destroyMethod = "shutdown")
	ExecutorService mailExecutor() {
		return Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "mail");
			t.setDaemon(true);
			return t;
		});
	}

	@Bean
	Mailer mailer(ExecutorService mailExecutor, @Value("${quizking.mail.host:}") String host,
			@Value("${quizking.mail.port:587}") int port, @Value("${quizking.mail.username:}") String username,
			@Value("${quizking.mail.password:}") String password,
			@Value("${quizking.mail.from:Quiz King <no-reply@localhost>}") String from) {
		if (host == null || host.isBlank()) {
			log.warn("MAIL_HOST is not set: emails (such as password reset links) are written to this log instead");
			return (to, subject, body) -> log.info("Email not sent (no MAIL_HOST) to {}: {}\n{}", to, subject, body);
		}
		JavaMailSenderImpl smtp = new JavaMailSenderImpl();
		smtp.setHost(host.trim());
		smtp.setPort(port);
		if (!username.isBlank()) {
			smtp.setUsername(username);
			smtp.setPassword(password);
			smtp.getJavaMailProperties().put("mail.smtp.auth", "true");
		}
		smtp.getJavaMailProperties().put("mail.smtp.starttls.enable", "true");
		smtp.getJavaMailProperties().put("mail.smtp.starttls.required", String.valueOf(port == 587));
		smtp.getJavaMailProperties().put("mail.smtp.ssl.enable", String.valueOf(port == 465));
		smtp.getJavaMailProperties().put("mail.smtp.connectiontimeout", "10000");
		smtp.getJavaMailProperties().put("mail.smtp.timeout", "10000");
		smtp.getJavaMailProperties().put("mail.smtp.writetimeout", "10000");
		log.info("Sending email through {}:{}", host, port);
		return (to, subject, body) -> mailExecutor.execute(() -> {
			try {
				SimpleMailMessage m = new SimpleMailMessage();
				m.setFrom(from);
				m.setTo(to);
				m.setSubject(subject);
				m.setText(body);
				smtp.send(m);
			}
			catch (RuntimeException e) {
				log.error("Could not send \"{}\" to {}: {}", subject, to, e.getMessage());
			}
		});
	}
}
