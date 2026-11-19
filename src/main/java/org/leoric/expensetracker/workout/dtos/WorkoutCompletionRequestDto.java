package org.leoric.expensetracker.workout.dtos;

import jakarta.validation.constraints.NotNull;

public record WorkoutCompletionRequestDto(@NotNull Boolean completed) {}
