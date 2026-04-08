package org.leoric.expensetracker.institution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.leoric.expensetracker.institution.models.constants.InstitutionType;

public record CreateInstitutionRequestDto(
		@NotBlank(message = "Name is required")
		String name,

		@NotNull(message = "Institution type is required")
		InstitutionType institutionType,

		String description
) {
}