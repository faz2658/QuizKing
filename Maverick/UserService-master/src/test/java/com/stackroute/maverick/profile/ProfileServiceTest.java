package com.stackroute.maverick.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stackroute.maverick.domain.UserRegistered;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

	@Mock
	ProfileRepository repo;

	@InjectMocks
	ProfileService service;

	static final UserProfile ADA = new UserProfile(1, "Ada", "ada@x.test", "hi", "London", null, List.of(2),
			Instant.EPOCH);

	@Test
	void registrationCreatesAProfileOnce() {
		when(repo.findById(1)).thenReturn(Optional.empty());
		when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

		UserProfile p = service.onRegistered(new UserRegistered(1, "Ada", "Ada@X.test", "USER", Instant.EPOCH));

		assertThat(p.email()).isEqualTo("ada@x.test");
		assertThat(p.favoriteCategoryIds()).isEmpty();
	}

	@Test
	void replayedRegistrationKeepsEdits() {
		when(repo.findById(1)).thenReturn(Optional.of(ADA));

		UserProfile p = service.onRegistered(new UserRegistered(1, "Ada", "ada@x.test", "USER", Instant.EPOCH));

		assertThat(p.aboutMe()).isEqualTo("hi");
		verify(repo, never()).save(any());
	}

	@Test
	void partialUpdateChangesOnlyGivenFields() {
		when(repo.findById(1)).thenReturn(Optional.of(ADA));
		when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

		UserProfile p = service.update(1, new ProfileChanges(" Ada L ", null, null, null, List.of(3, 4)));

		assertThat(p.userName()).isEqualTo("Ada L");
		assertThat(p.location()).isEqualTo("London");
		assertThat(p.email()).isEqualTo("ada@x.test");
		assertThat(p.favoriteCategoryIds()).containsExactly(3, 4);
	}

	@Test
	void missingUserIsNotFoundInsteadOfNull() {
		when(repo.findById(9)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(9)).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.delete(9)).isInstanceOf(NotFoundException.class);
	}
}
