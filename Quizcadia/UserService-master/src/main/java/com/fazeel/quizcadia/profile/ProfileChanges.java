package com.fazeel.quizcadia.profile;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Partial update: null fields are left unchanged. Email and id cannot be changed here. The name follows the
 * same rules as sign-up in the login service (InputRules there, core/validation.ts in the app).
 */
public record ProfileChanges(
		@Size(min = 1, max = ProfileChanges.NAME_MAX, message = ProfileChanges.NAME_SIZE)
		@Pattern(regexp = ProfileChanges.NAME_PATTERN, message = ProfileChanges.NAME_CHARS) String userName,
		@Size(max = 500, message = "The about-me text can be at most 500 characters.") String aboutMe,
		@Size(max = 100, message = "The location can be at most 100 characters.") String location,
		@Size(max = 500, message = "The image link can be at most 500 characters.") String image,
		@Size(max = 20, message = "Pick at most 20 favorite categories.")
		List<@NotNull(message = "Category ids must be positive numbers.") @Positive(message = "Category ids must be positive numbers.") Integer> favoriteCategoryIds) {

	static final int NAME_MAX = 60;
	static final String NAME_PATTERN = "[\\p{L}\\p{M}\\p{N}][\\p{L}\\p{M}\\p{N} .'_-]*";
	static final String NAME_SIZE = "Enter a name of at most 60 characters.";
	static final String NAME_CHARS = "The name can use letters, numbers, spaces and . ' _ - and must start with a letter or number.";

	public ProfileChanges {
		userName = userName == null ? null : userName.trim();
	}
}
