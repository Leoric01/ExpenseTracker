package org.leoric.expensetracker.habit.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessService;
import org.leoric.expensetracker.habit.dtos.HabitDayOverviewDto;
import org.leoric.expensetracker.habit.dtos.HabitResponseDto;
import org.leoric.expensetracker.habit.dtos.HabitUpsertRequestDto;
import org.leoric.expensetracker.habit.dtos.HabitWeekOverviewResponseDto;
import org.leoric.expensetracker.habit.services.interfaces.HabitService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_MEMBER;
import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_OWNER;

@RestController
@RequestMapping("/api/habit")
@RequiredArgsConstructor
public class HabitController {

	private final HabitService habitService;
	private final ExpenseTrackerAccessService expenseTrackerAccessService;

	@PostMapping("/{trackerId}")
	public ResponseEntity<HabitResponseDto> habitCreate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@Valid @RequestBody HabitUpsertRequestDto request) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER
		);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(habitService.habitCreate(currentUser, trackerId, request));
	}

	@GetMapping("/{trackerId}")
	public ResponseEntity<Page<HabitResponseDto>> habitFindAll(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@RequestParam(required = false) String search,
			@RequestParam(required = false) Boolean active,
			@ParameterObject Pageable pageable) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);
		return ResponseEntity.ok(
				habitService.habitFindAll(currentUser, trackerId, search, active, pageable)
		);
	}

	@GetMapping("/{trackerId}/{habitId}")
	public ResponseEntity<HabitResponseDto> habitFindById(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID habitId) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);
		return ResponseEntity.ok(
				habitService.habitFindById(currentUser, trackerId, habitId)
		);
	}

	@PatchMapping("/{trackerId}/{habitId}")
	public ResponseEntity<HabitResponseDto> habitUpdate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID habitId,
			@Valid @RequestBody HabitUpsertRequestDto request) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER
		);
		return ResponseEntity.ok(
				habitService.habitUpdate(currentUser, trackerId, habitId, request)
		);
	}

	@PatchMapping("/{trackerId}/{habitId}/activate")
	public ResponseEntity<Void> habitActivate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID habitId) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER
		);
		habitService.habitActivate(currentUser, trackerId, habitId);
		return ResponseEntity.noContent().build();
	}

	@PatchMapping("/{trackerId}/{habitId}/deactivate")
	public ResponseEntity<Void> habitDeactivate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID habitId) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER
		);
		habitService.habitDeactivate(currentUser, trackerId, habitId);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/{trackerId}/{habitId}")
	public ResponseEntity<Void> habitDelete(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID habitId) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER
		);
		habitService.habitDelete(currentUser, trackerId, habitId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{trackerId}/agenda")
	public ResponseEntity<HabitDayOverviewDto> habitFindAgendaForDate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@RequestParam(required = false) LocalDate date) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);
		return ResponseEntity.ok(
				habitService.habitFindAgendaForDate(
						currentUser,
						trackerId,
						date != null ? date : LocalDate.now()
				)
		);
	}

	@GetMapping("/{trackerId}/week")
	public ResponseEntity<HabitWeekOverviewResponseDto> habitFindWeekOverview(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@RequestParam(required = false) LocalDate weekStart) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);
		return ResponseEntity.ok(
				habitService.habitFindWeekOverview(
						currentUser,
						trackerId,
						weekStart != null ? weekStart : LocalDate.now()
				)
		);
	}
}