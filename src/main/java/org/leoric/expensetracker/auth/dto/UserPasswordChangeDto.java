package org.leoric.expensetracker.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserPasswordChangeDto(
		@NotBlank(message = "Current password is required")
		String oldPassword,

		@NotBlank(message = "New password is required")
		@Size(min = 8, message = "Password must be at least 8 characters long")
		String newPassword,

		@NotBlank(message = "Password confirmation is required")
		String newConfirmationPassword
) {
}