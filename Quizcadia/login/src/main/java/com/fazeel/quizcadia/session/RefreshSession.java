package com.fazeel.quizcadia.session;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * One refresh token of a sign-in. Every refresh uses the token up and adds the next one to the same
 * {@code familyId} (one family = one signed-in browser). Only a SHA-256 hash of the token is stored.
 * Guests have no account, so their row carries the guest's name.
 */
@Entity
@Table(name = "refresh_sessions", indexes = { @Index(name = "idx_refresh_sessions_family", columnList = "familyId"),
		@Index(name = "idx_refresh_sessions_user", columnList = "userId") })
public class RefreshSession {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 36)
	private String familyId;

	@Column(nullable = false)
	private Integer userId;

	@Column(nullable = false)
	private boolean guest;

	@Column(length = 60)
	private String guestName;

	@Column(nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(nullable = false)
	private Instant createdAt;

	/** This token stops working here if it isn't used (the idle limit). */
	@Column(nullable = false)
	private Instant expiresAt;

	/** The whole sign-in ends here however often it is refreshed. */
	@Column(nullable = false)
	private Instant familyExpiresAt;

	private Instant usedAt;

	private Instant revokedAt;

	protected RefreshSession() {
	}

	RefreshSession(String familyId, int userId, boolean guest, String guestName, String tokenHash, Instant createdAt,
			Instant expiresAt, Instant familyExpiresAt) {
		this.familyId = familyId;
		this.userId = userId;
		this.guest = guest;
		this.guestName = guestName;
		this.tokenHash = tokenHash;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
		this.familyExpiresAt = familyExpiresAt;
	}

	public Long getId() {
		return id;
	}

	public String getFamilyId() {
		return familyId;
	}

	public int getUserId() {
		return userId;
	}

	public boolean isGuest() {
		return guest;
	}

	public String getGuestName() {
		return guestName;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getFamilyExpiresAt() {
		return familyExpiresAt;
	}

	public Instant getUsedAt() {
		return usedAt;
	}

	public Instant getRevokedAt() {
		return revokedAt;
	}

	boolean expiredAt(Instant now) {
		return !now.isBefore(expiresAt) || !now.isBefore(familyExpiresAt);
	}

	void revoke(Instant now) {
		if (revokedAt == null) {
			revokedAt = now;
		}
	}
}
