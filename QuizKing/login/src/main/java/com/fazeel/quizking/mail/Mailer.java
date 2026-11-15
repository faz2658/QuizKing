package com.fazeel.quizking.mail;

/** Sends plain-text emails. Sending happens in the background, so callers never wait on the mail server. */
public interface Mailer {

	void send(String to, String subject, String body);
}
