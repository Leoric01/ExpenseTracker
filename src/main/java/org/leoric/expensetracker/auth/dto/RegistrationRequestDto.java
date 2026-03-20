package org.leoric.expensetracker.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrationRequestDto(
		@NotBlank(message = "First name is required")
		String firstName,

		@NotBlank(message = "Last name is required")
		String lastName,

		@Email(message = "Invalid email format")
		@NotBlank(message = "Email is required")
		String email,

		@NotBlank(message = "Password is required")
		@Size(min = 3, message = "Password must be at least 3 characters long")
		String password
) {
}