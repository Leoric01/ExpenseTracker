package org.leoric.expensetracker.habit.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessService;
import org.leoric.expensetracker.habit.dtos.HabitCompletionResponseDto;
import org.leoric.expensetracker.habit.dtos.HabitCompletionUpsertRequestDto;
import org.leoric.expensetracker.habit.services.interfaces.HabitCompletionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_MEMBER;
import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_OWNER;

@RestController
@RequestMapping("/api/habit-completion")
@RequiredArgsConstructor
public class HabitCompletionController {

	private final HabitCompletionService habitCompletionService;
	private final ExpenseTrackerAccessService expenseTrackerAccessService;

	@PutMapping("/{trackerId}")
	public ResponseEntity<HabitCompletionResponseDto> habitCompletionUpsert(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@Valid @RequestBody HabitCompletionUpsertRequestDto request) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);
		return ResponseEntity.ok(
				habitCompletionService.habitCompletionUpsert(currentUser, trackerId, request)
		);
	}
}