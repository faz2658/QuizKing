package com.fazeel.quizcadia.push;

import java.time.Duration;

/** Delivers one push message to one browser subscription (see {@link WebPush}). */
public interface PushSender {

	WebPush.Result send(String endpoint, String p256dh, String auth, String payload, Duration ttl)
			throws java.io.IOException, InterruptedException;
}
