package org.leoric.expensetracker.food.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessService;
import org.leoric.expensetracker.food.dtos.ManualNutritionTargetRequestDto;
import org.leoric.expensetracker.food.dtos.NutritionTargetResponseDto;
import org.leoric.expensetracker.food.services.interfaces.NutritionTargetService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_MEMBER;
import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_OWNER;

@RestController
@RequestMapping("/api/nutrition-target")
@RequiredArgsConstructor
public class NutritionTargetController {

	private final NutritionTargetService nutritionTargetService;
	private final ExpenseTrackerAccessService expenseTrackerAccessService;

	@PostMapping("/{trackerId}/{goalPlanId}/initial")
	public ResponseEntity<NutritionTargetResponseDto> nutritionTargetGenerateInitial(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID goalPlanId) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);

		return ResponseEntity.status(HttpStatus.CREATED)
				.body(nutritionTargetService.nutritionTargetGenerateInitial(currentUser, trackerId, goalPlanId));
	}

	@GetMapping("/{trackerId}/current")
	public ResponseEntity<NutritionTargetResponseDto> nutritionTargetFindCurrent(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);

		return ResponseEntity.ok(nutritionTargetService.nutritionTargetFindCurrent(currentUser, trackerId));
	}

	@GetMapping("/{trackerId}")
	public ResponseEntity<Page<NutritionTargetResponseDto>> nutritionTargetFindAll(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@ParameterObject Pageable pageable) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);

		return ResponseEntity.ok(nutritionTargetService.nutritionTargetFindAll(currentUser, trackerId, pageable));
	}

	@PostMapping("/{trackerId}/{goalPlanId}/manual-override")
	public ResponseEntity<NutritionTargetResponseDto> nutritionTargetManualOverride(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID goalPlanId,
			@Valid @RequestBody ManualNutritionTargetRequestDto request) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER
		);

		return ResponseEntity.status(HttpStatus.CREATED)
				.body(nutritionTargetService.nutritionTargetManualOverride(currentUser, trackerId, goalPlanId, request));
	}
}