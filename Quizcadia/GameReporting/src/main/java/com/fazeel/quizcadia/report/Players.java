package com.fazeel.quizcadia.report;

/** Who a user id belongs to. Guests get ids from a fixed range in the login service; bots have ids of 0 or less. */
public final class Players {

	/** Guest ids are {@code GUEST_ID_BASE} and up (see the login service's guest sign-in). */
	public static final int GUEST_ID_BASE = 900_000_000;

	private Players() {
	}

	public static boolean isGuest(int userId) {
		return userId >= GUEST_ID_BASE;
	}

	/** A real account: not a guest, not a bot. Only accounts appear on public leaderboards. */
	public static boolean isAccount(int userId) {
		return userId > 0 && !isGuest(userId);
	}
}
