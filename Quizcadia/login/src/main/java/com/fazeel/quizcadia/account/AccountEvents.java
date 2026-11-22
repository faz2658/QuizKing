package com.fazeel.quizcadia.account;

/** Where news of a new account goes (Kafka in production, a log line when Kafka is off). */
public interface AccountEvents {

	void registered(UserRegistered event);
}
