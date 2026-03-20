package org.leoric.expensetracker.auth.dto;

public record UserProfileUpdateDto(
		String firstName,
		String lastName
) {
}