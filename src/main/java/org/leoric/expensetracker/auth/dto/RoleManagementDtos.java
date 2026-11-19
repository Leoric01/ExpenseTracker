package org.leoric.expensetracker.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.leoric.expensetracker.auth.models.GlobalRoleRequest;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class RoleManagementDtos {
	private RoleManagementDtos() {}

	public record RoleRequest(@NotBlank String role) {}
	public record RoleSelection(@NotEmpty Set<@NotBlank String> roles, @NotNull Set<@NotBlank String> expectedRoles) {}
	public record TrackerRoleSelection(@NotNull Set<@NotBlank String> roles, @NotNull Set<@NotBlank String> expectedRoles) {}
	public record GlobalRequestResponse(UUID id, UUID userId, String userName, String userEmail,
			String role, String status, Instant requestDate, Instant decisionDate, String decidedBy) {
		public static GlobalRequestResponse from(GlobalRoleRequest request) {
			return new GlobalRequestResponse(request.getId(), request.getUser().getId(),
					request.getUser().getFullname(), request.getUser().getEmail(), request.getRoleName(),
					request.getStatus().name(), request.getRequestDate(), request.getDecisionDate(),
					request.getDecidedBy() == null ? null : request.getDecidedBy().getFullname());
		}
	}
}
