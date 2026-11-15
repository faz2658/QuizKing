package com.stackroute.maverick.profile;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Public profile of a player. Credentials live only in the login service. */
@Document(collection = "profiles")
public record UserProfile(@Id int userId, String userName, @Indexed(unique = true) String email, String aboutMe,
		String location, String image, List<Integer> favoriteCategoryIds, Instant createdAt) {

	UserProfile withChanges(ProfileChanges c) {
		return new UserProfile(userId, c.userName() != null ? c.userName().trim() : userName, email,
				c.aboutMe() != null ? c.aboutMe() : aboutMe, c.location() != null ? c.location() : location,
				c.image() != null ? c.image() : image,
				c.favoriteCategoryIds() != null ? List.copyOf(c.favoriteCategoryIds()) : favoriteCategoryIds,
				createdAt);
	}
}
