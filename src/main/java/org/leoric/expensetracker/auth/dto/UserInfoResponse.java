package org.leoric.expensetracker.auth.dto;

import java.util.List;
import java.util.UUID;

public record UserInfoResponse(
		UUID id,
		String email,
		String firstName,
		String lastName,
		List<String> roles
) {
}