package org.leoric.expensetracker.auth.controllers;

import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.dto.UserInfoResponseDto;
import org.leoric.expensetracker.auth.dto.UserPasswordChangeDto;
import org.leoric.expensetracker.auth.dto.UserProfileUpdateDto;
import org.leoric.expensetracker.auth.dto.UserResponseFullDto;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.auth.services.interfaces.UserService;
import org.leoric.expensetracker.restaurant.services.RestaurantFileCleanupService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/profile")
public class ProfileController {

	private final UserService userService;
	private final RestaurantFileCleanupService restaurantFileCleanupService;

	@GetMapping("/me")
	public ResponseEntity<UserInfoResponseDto> profileMe(@AuthenticationPrincipal User currentUser) {
		return ResponseEntity.ok(userService.profileMe(currentUser));
	}

	@GetMapping
	@Secured("ADMIN")
	public ResponseEntity<Page<UserResponseFullDto>> profileFindAllPageable(
			@RequestParam(required = false) String search,
			@ParameterObject Pageable pageable) {
		return ResponseEntity.ok(userService.profileFindAllPageable(search, pageable));
	}

	@PatchMapping("/update")
	public ResponseEntity<UserResponseFullDto> profileUpdate(
			@AuthenticationPrincipal User currentUser,
			@RequestBody UserProfileUpdateDto dto) {
		return ResponseEntity.ok(userService.profileUpdate(currentUser, dto));
	}

	@PatchMapping("/change-password")
	public ResponseEntity<Void> profileChangePassword(
			@AuthenticationPrincipal User currentUser,
			@RequestBody UserPasswordChangeDto dto) {
		userService.profileChangePassword(currentUser, dto);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/me")
	public ResponseEntity<Void> profileDeleteMe(@AuthenticationPrincipal User currentUser) {
		userService.profileDeleteMe(currentUser);
		long pendingCleanup = restaurantFileCleanupService.process();
		return ResponseEntity.noContent().header("X-Restaurant-Pending-Cleanup", Long.toString(pendingCleanup)).build();
	}
}