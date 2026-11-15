package com.fazeel.quizking.invite;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** A friend who joined through someone's invite link; keyed by the new player, so each counts once. */
@Document(collection = "referrals")
public record Referral(@Id int userId, String name, @Indexed int referrerId, Instant joinedAt) {
}
