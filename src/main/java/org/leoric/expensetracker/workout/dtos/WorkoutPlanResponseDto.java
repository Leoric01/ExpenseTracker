package org.leoric.expensetracker.workout.dtos;

import java.util.List;

public record WorkoutPlanResponseDto(String title, String weekLabel, List<Day> days) {
	public record Day(String id, String label, String name, String focus, List<Exercise> exercises) {}
	public record Exercise(String id, String name, String sets, String description, boolean completed) {}
}
