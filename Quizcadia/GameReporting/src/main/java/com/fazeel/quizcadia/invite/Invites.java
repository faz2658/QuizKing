package com.fazeel.quizcadia.invite;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.fazeel.quizcadia.domain.UserRegistered;

/**
 * Invite rewards: when a friend creates an account from your link, you both get a streak shield (it covers one
 * missed day in a streak), and inviting friends earns the Recruiter and Ambassador badges.
 */
@Service
public class Invites {

	private static final Logger log = LoggerFactory.getLogger(Invites.class);

	private final ReferralRepository referrals;
	private final Clock clock;

	public Invites(ReferralRepository referrals, Clock clock) {
		this.referrals = referrals;
		this.clock = clock;
	}

	public void registered(UserRegistered e) {
		if (e.referredBy() == null || e.referredBy() <= 0 || e.referredBy() == e.userId()) {
			return;
		}
		try {
			referrals.insert(new Referral(e.userId(), e.userName(), e.referredBy(),
					e.registeredAt() != null ? e.registeredAt() : clock.instant()));
			log.info("Player {} joined through {}'s invite", e.userId(), e.referredBy());
		}
		catch (DuplicateKeyException alreadyCounted) {
			// the same event delivered twice
		}
	}

	/** When each friend this player invited joined, oldest first. */
	public List<Instant> invited(int userId) {
		return referrals.findByReferrerIdOrderByJoinedAtAsc(userId).stream().map(Referral::joinedAt).toList();
	}

	/** One per friend invited, plus one for having joined through a friend's link. */
	public int shields(int userId) {
		return invited(userId).size() + (referrals.existsById(userId) ? 1 : 0);
	}

	/** Invite codes are account ids in base 36, the same as the login service reads them. */
	public static String code(int userId) {
		return Integer.toString(userId, 36);
	}
}
