package com.fazeel.maverick.account;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * A "forgot password" request. Only a SHA-256 hash of the emailed token is stored, so a copy of the
 * database can't be used to reset anyone's password. Each token works once and expires.
 */
@Entity
@Table(name = "password_resets", indexes = @Index(name = "idx_password_resets_user", columnList = "userId"))
public class PasswordReset {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Integer userId;

	@Column(nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant expiresAt;

	private Instant usedAt;

	protected PasswordReset() {
	}

	public PasswordReset(int userId, String tokenHash, Instant createdAt, Instant expiresAt) {
		this.userId = userId;
		this.tokenHash = tokenHash;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	public boolean usableAt(Instant now) {
		return usedAt == null && now.isBefore(expiresAt);
	}

	void use(Instant now) {
		usedAt = now;
	}

	public Integer getUserId() {
		return userId;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}
}
