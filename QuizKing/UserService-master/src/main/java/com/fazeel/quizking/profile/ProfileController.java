package com.fazeel.quizking.profile;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/users")
public class ProfileController {

	private final ProfileService service;

	public ProfileController(ProfileService service) {
		this.service = service;
	}

	@GetMapping
	public List<UserProfile> users(@RequestParam(required = false) String email) {
		return email == null ? service.all() : List.of(service.byEmail(email));
	}

	@GetMapping("/{userId}")
	public UserProfile user(@PathVariable int userId) {
		return service.get(userId);
	}

	@PatchMapping("/{userId}")
	public UserProfile update(@PathVariable int userId, @Valid @RequestBody ProfileChanges changes) {
		return service.update(userId, changes);
	}

	@DeleteMapping("/{userId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable int userId) {
		service.delete(userId);
	}

	@ExceptionHandler(NotFoundException.class)
	ProblemDetail notFound(NotFoundException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
	}
}
