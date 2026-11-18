package com.fazeel.quizcadia.api;

/**
 * Rules for what players type when they sign up or sign in. The Angular app checks the same rules
 * (frontend/src/app/core/validation.ts), so keep the two identical.
 */
public final class InputRules {

	private InputRules() {
	}

	/** Display names: the column is 60 wide, and Google names are cut to 60 too. One letter is fine. */
	public static final int NAME_MAX = 60;

	/** Letters (any language), numbers, spaces and . ' _ -, starting with a letter or number. */
	public static final String NAME_PATTERN = "[\\p{L}\\p{M}\\p{N}][\\p{L}\\p{M}\\p{N} .'_-]*";

	public static final int EMAIL_MAX = 254;

	/** New accounts need a dot in the domain (name@example.com). */
	public static final String EMAIL_PATTERN = "[^@\\s]+@[^@\\s]+\\.[^@\\s]+";

	/** Signing in and "forgot password" only check the address has the shape of one, so older accounts still work. */
	public static final String EMAIL_LOOSE_PATTERN = "[^@\\s]+@[^@\\s]+";

	/** BCrypt only reads the first 72 bytes, and Spring Security refuses longer passwords. */
	public static final int PASSWORD_MIN = 8;
	public static final int PASSWORD_MAX = 72;

	public static final String NAME_REQUIRED = "Enter a name.";
	public static final String NAME_SIZE = "The name can be at most 60 characters.";
	public static final String NAME_CHARS = "The name can use letters, numbers, spaces and . ' _ - and must start with a letter or number.";
	public static final String EMAIL_REQUIRED = "Enter your email address.";
	public static final String EMAIL_INVALID = "Enter a valid email address, like name@example.com.";
	public static final String EMAIL_SIZE = "The email address can be at most 254 characters.";
	public static final String PASSWORD_REQUIRED = "Enter a password.";
	public static final String PASSWORD_SIZE = "The password needs 8 to 72 characters.";
	public static final String PASSWORD_TOO_LONG = "The password can be at most 72 characters.";
}
