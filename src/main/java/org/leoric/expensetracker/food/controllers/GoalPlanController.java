package org.leoric.expensetracker.food.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessService;
import org.leoric.expensetracker.food.dtos.CreateGoalPlanRequestDto;
import org.leoric.expensetracker.food.dtos.GoalPlanResponseDto;
import org.leoric.expensetracker.food.dtos.UpdateGoalPlanRequestDto;
import org.leoric.expensetracker.food.services.interfaces.GoalPlanService;
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

import java.util.UUID;

import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_MEMBER;
import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_OWNER;

@RestController
@RequestMapping("/api/goal-plan")
@RequiredArgsConstructor
public class GoalPlanController {

	private final GoalPlanService goalPlanService;
	private final ExpenseTrackerAccessService expenseTrackerAccessService;

	@PostMapping("/{trackerId}")
	public ResponseEntity<GoalPlanResponseDto> goalPlanCreate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@Valid @RequestBody CreateGoalPlanRequestDto request) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);

		return ResponseEntity.status(HttpStatus.CREATED)
				.body(goalPlanService.goalPlanCreate(currentUser, trackerId, request));
	}

	@GetMapping("/{trackerId}")
	public ResponseEntity<Page<GoalPlanResponseDto>> goalPlanFindAll(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@RequestParam(required = false) String search,
			@ParameterObject Pageable pageable) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);

		return ResponseEntity.ok(goalPlanService.goalPlanFindAll(currentUser, trackerId, search, pageable));
	}

	@GetMapping("/{trackerId}/{goalPlanId}")
	public ResponseEntity<GoalPlanResponseDto> goalPlanFindById(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID goalPlanId) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);

		return ResponseEntity.ok(goalPlanService.goalPlanFindById(currentUser, trackerId, goalPlanId));
	}

	@PatchMapping("/{trackerId}/{goalPlanId}")
	public ResponseEntity<GoalPlanResponseDto> goalPlanUpdate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID goalPlanId,
			@Valid @RequestBody UpdateGoalPlanRequestDto request) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER
		);

		return ResponseEntity.ok(goalPlanService.goalPlanUpdate(currentUser, trackerId, goalPlanId, request));
	}

	@PostMapping("/{trackerId}/{goalPlanId}/activate")
	public ResponseEntity<GoalPlanResponseDto> goalPlanActivate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID goalPlanId) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER
		);

		return ResponseEntity.ok(goalPlanService.goalPlanActivate(currentUser, trackerId, goalPlanId));
	}

	@DeleteMapping("/{trackerId}/{goalPlanId}")
	public ResponseEntity<Void> goalPlanDeactivate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID goalPlanId) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER
		);

		goalPlanService.goalPlanDeactivate(currentUser, trackerId, goalPlanId);
		return ResponseEntity.noContent().build();
	}
}