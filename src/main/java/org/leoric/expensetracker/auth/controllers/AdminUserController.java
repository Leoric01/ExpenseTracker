package org.leoric.expensetracker.auth.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.dto.AdminPasswordResetDto;
import org.leoric.expensetracker.auth.services.interfaces.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/users")
public class AdminUserController {

	private final UserService userService;

	@Secured("ADMIN")
	@PatchMapping("/reset-password")
	public ResponseEntity<Void> resetPassword(@Valid @RequestBody AdminPasswordResetDto dto) {
		userService.adminResetPassword(dto);
		return ResponseEntity.noContent().build();
	}
}