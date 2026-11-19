package org.leoric.expensetracker.workout.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessService;
import org.leoric.expensetracker.workout.dtos.WorkoutCompletionRequestDto;
import org.leoric.expensetracker.workout.dtos.WorkoutPlanResponseDto;
import org.leoric.expensetracker.workout.dtos.WorkoutPlanUpsertRequestDto;
import org.leoric.expensetracker.workout.services.interfaces.WorkoutService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_MEMBER;
import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_OWNER;

@RestController
@RequestMapping("/api/workout")
@RequiredArgsConstructor
public class WorkoutController {
	private final WorkoutService workoutService;
	private final ExpenseTrackerAccessService expenseTrackerAccessService;

	@GetMapping("/{trackerId}")
	public ResponseEntity<WorkoutPlanResponseDto> workoutFind(
			@AuthenticationPrincipal User currentUser, @PathVariable UUID trackerId) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.ok(workoutService.workoutFind(currentUser, trackerId));
	}

	@PutMapping("/{trackerId}")
	public ResponseEntity<WorkoutPlanResponseDto> workoutUpsert(
			@AuthenticationPrincipal User currentUser, @PathVariable UUID trackerId,
			@Valid @RequestBody WorkoutPlanUpsertRequestDto request) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER);
		return ResponseEntity.ok(workoutService.workoutUpsert(currentUser, trackerId, request));
	}

	@PutMapping("/{trackerId}/days/{dayId}/exercises/{exerciseId}/completion")
	public ResponseEntity<Void> workoutSetCompletion(
			@AuthenticationPrincipal User currentUser, @PathVariable UUID trackerId,
			@PathVariable String dayId, @PathVariable String exerciseId,
			@Valid @RequestBody WorkoutCompletionRequestDto request) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		workoutService.workoutSetCompletion(currentUser, trackerId, dayId, exerciseId, request.completed());
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/{trackerId}/completion")
	public ResponseEntity<Void> workoutReset(
			@AuthenticationPrincipal User currentUser, @PathVariable UUID trackerId) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		workoutService.workoutReset(currentUser, trackerId);
		return ResponseEntity.noContent().build();
	}
}
