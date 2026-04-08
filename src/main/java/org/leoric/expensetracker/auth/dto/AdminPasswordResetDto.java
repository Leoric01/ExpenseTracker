package org.leoric.expensetracker.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminPasswordResetDto(
		@NotBlank(message = "Email is required")
		@Email(message = "Email should be valid")
		String email,

		@NotBlank(message = "New password is required")
		@Size(min = 8, message = "Password must be at least 8 characters long")
		String newPassword
) {
}