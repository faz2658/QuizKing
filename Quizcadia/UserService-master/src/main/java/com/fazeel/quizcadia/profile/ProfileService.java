package com.fazeel.quizcadia.profile;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.fazeel.quizcadia.domain.UserRegistered;

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

	public UserProfile get(int userId) {
		return profiles.findById(userId).orElseThrow(() -> new NotFoundException("No user " + userId));
	}

	public UserProfile byEmail(String email) {
		return profiles.findByEmail(email.trim().toLowerCase(Locale.ROOT))
				.orElseThrow(() -> new NotFoundException("No user with that email"));
	}

	public List<UserProfile> all() {
		return profiles.findAll();
	}

	public UserProfile update(int userId, ProfileChanges changes) {
		return profiles.save(get(userId).withChanges(changes));
	}

	public void delete(int userId) {
		if (!profiles.existsById(userId)) {
			throw new NotFoundException("No user " + userId);
		}
		profiles.deleteById(userId);
	}
}
