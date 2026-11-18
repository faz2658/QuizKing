package com.fazeel.quizcadia.profile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fazeel.quizcadia.config.SecurityConfig;

/** Profile edits follow the sign-up name rules; a bad edit is a 400 problem and changes nothing. */
@Import(SecurityConfig.class)
@WebMvcTest(ProfileController.class)
class ProfileValidationTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	ProfileService service;

	ResultActions edit(String json) throws Exception {
		return mvc.perform(patch("/api/v1/users/7").contentType(MediaType.APPLICATION_JSON).content(json)
				.with(jwt().jwt(j -> j.subject("7")).authorities(new SimpleGrantedAuthority("ROLE_USER"))));
	}

	@Test
	void aValidEditGoesThrough() throws Exception {
		when(service.update(eq(7), any())).thenReturn(new UserProfile(7, "José Álvarez", "ada@example.test", null, null,
				null, List.of(3), Instant.EPOCH));
		edit("{\"userName\":\"  José Álvarez \",\"favoriteCategoryIds\":[3]}").andExpect(status().isOk());
		verify(service).update(7, new ProfileChanges("José Álvarez", null, null, null, List.of(3)));
	}

	@Test
	void badNamesAreRejectedWithTheSameMessagesAsSignUp() throws Exception {
		edit("{\"userName\":\"<script>\"}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(ProfileChanges.NAME_CHARS))
				.andExpect(jsonPath("$.errors.userName").value(ProfileChanges.NAME_CHARS));
		edit("{\"userName\":\"   \"}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.userName").exists());
		edit("{\"userName\":\"" + "a".repeat(61) + "\"}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(ProfileChanges.NAME_SIZE));
		verify(service, never()).update(eq(7), any());
	}

	@Test
	void otherFieldsHaveLimitsAndTheDetailListsEachProblem() throws Exception {
		edit("{\"aboutMe\":\"" + "x".repeat(501) + "\",\"location\":\"" + "y".repeat(101)
				+ "\",\"favoriteCategoryIds\":[0]}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Invalid input"))
				.andExpect(jsonPath("$.detail").value("The about-me text can be at most 500 characters. "
						+ "The location can be at most 100 characters. Category ids must be positive numbers."));
		edit("{not json").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(ValidationProblems.UNREADABLE));
		verify(service, never()).update(eq(7), any());
	}
}
