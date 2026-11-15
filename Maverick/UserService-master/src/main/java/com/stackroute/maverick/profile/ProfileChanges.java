package com.stackroute.maverick.profile;

import java.util.List;

import jakarta.validation.constraints.Size;

/** Partial update: null fields are left unchanged. Email and id cannot be changed here. */
public record ProfileChanges(@Size(min = 1, max = 60) String userName, @Size(max = 500) String aboutMe,
		@Size(max = 100) String location, @Size(max = 500) String image,
		@Size(max = 20) List<Integer> favoriteCategoryIds) {
}
