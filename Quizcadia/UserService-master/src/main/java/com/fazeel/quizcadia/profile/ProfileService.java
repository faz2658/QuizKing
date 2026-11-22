package com.fazeel.quizcadia.profile;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.fazeel.quizcadia.domain.UserRegistered;

/** Reads and changes profiles; emails are stored in lower case. */
@Service
public class ProfileService {

	private final ProfileRepository profiles;

	public ProfileService(ProfileRepository profiles) {
		this.profiles = profiles;
	}

	/** Creates the profile of a newly registered user. Replays keep any edits made since. */
	public UserProfile onRegistered(UserRegistered e) {
		return profiles.findById(e.userId()).orElseGet(() -> profiles.save(new UserProfile(e.userId(), e.userName(),
				e.email().toLowerCase(Locale.ROOT), null, null, null, List.of(), e.registeredAt())));
	}

	/** The profile; NotFoundException if there is none. */
	public UserProfile get(int userId) {
		return profiles.findById(userId).orElseThrow(() -> new NotFoundException("No user " + userId));
	}

	/** The profile with that email, ignoring case and spaces around it. */
	public UserProfile byEmail(String email) {
		return profiles.findByEmail(email.trim().toLowerCase(Locale.ROOT))
				.orElseThrow(() -> new NotFoundException("No user with that email"));
	}

	/** Every profile. */
	public List<UserProfile> all() {
		return profiles.findAll();
	}

	/** Applies the changes and saves the profile. */
	public UserProfile update(int userId, ProfileChanges changes) {
		return profiles.save(get(userId).withChanges(changes));
	}

	/** Deletes the profile; NotFoundException if there is none. */
	public void delete(int userId) {
		if (!profiles.existsById(userId)) {
			throw new NotFoundException("No user " + userId);
		}
		profiles.deleteById(userId);
	}
}
