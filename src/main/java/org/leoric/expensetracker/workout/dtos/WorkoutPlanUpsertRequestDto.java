package org.leoric.expensetracker.workout.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record WorkoutPlanUpsertRequestDto(
		@NotBlank @Size(max = 120) String title,
		@NotBlank @Size(max = 120) String weekLabel,
		@NotNull @Valid List<@NotNull @Valid Day> days
) {
	public record Day(
			@NotBlank @Size(max = 64) @Pattern(regexp = "[a-z0-9][a-z0-9_-]*") String id,
			@NotBlank @Size(max = 40) String label,
			@NotBlank @Size(max = 120) String name,
			@NotBlank @Size(max = 255) String focus,
			@NotNull @Valid List<@NotNull @Valid Exercise> exercises
	) {}

	public record Exercise(
			@NotBlank @Size(max = 64) @Pattern(regexp = "[a-z0-9][a-z0-9_-]*") String id,
			@NotBlank @Size(max = 120) String name,
			@NotBlank @Size(max = 120) String sets,
			@Size(max = 500) String description
	) {}
}
