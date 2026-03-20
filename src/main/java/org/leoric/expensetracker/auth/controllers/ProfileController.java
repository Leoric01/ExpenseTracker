package org.leoric.expensetracker.auth.controllers;

import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.dto.UserInfoResponse;
import org.leoric.expensetracker.auth.dto.UserPasswordChangeDto;
import org.leoric.expensetracker.auth.dto.UserProfileUpdateDto;
import org.leoric.expensetracker.auth.dto.UserResponseFullDto;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.auth.services.interfaces.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/profile")
public class ProfileController {

	private final UserService userService;

	@GetMapping("/me")
	public ResponseEntity<UserInfoResponse> me(@AuthenticationPrincipal User currentUser) {
		return ResponseEntity.ok(userService.getCurrentUser(currentUser));
	}

	@PatchMapping("/update")
	public ResponseEntity<UserResponseFullDto> updateProfile(
			@AuthenticationPrincipal User currentUser,
			@RequestBody UserProfileUpdateDto dto) {
		return ResponseEntity.ok(userService.updateProfile(currentUser, dto));
	}

	@PatchMapping("/change-password")
	public ResponseEntity<Void> changePassword(
			@AuthenticationPrincipal User currentUser,
			@RequestBody UserPasswordChangeDto dto) {
		userService.changePassword(currentUser, dto);
		return ResponseEntity.noContent().build();
	}
}