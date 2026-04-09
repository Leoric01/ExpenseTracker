package org.leoric.expensetracker.food.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessService;
import org.leoric.expensetracker.food.dtos.NutritionProfileResponseDto;
import org.leoric.expensetracker.food.dtos.UpsertNutritionProfileRequestDto;
import org.leoric.expensetracker.food.services.interfaces.NutritionProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_MEMBER;
import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_OWNER;

@RestController
@RequestMapping("/api/nutrition-profile")
@RequiredArgsConstructor
public class NutritionProfileController {

	private final NutritionProfileService nutritionProfileService;
	private final ExpenseTrackerAccessService expenseTrackerAccessService;

	@PutMapping("/{trackerId}")
	public ResponseEntity<NutritionProfileResponseDto> nutritionProfileUpsert(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@Valid @RequestBody UpsertNutritionProfileRequestDto request) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);

		return ResponseEntity.ok(nutritionProfileService.nutritionProfileUpsert(currentUser, trackerId, request));
	}

	@GetMapping("/{trackerId}")
	public ResponseEntity<NutritionProfileResponseDto> nutritionProfileFind(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);

		return ResponseEntity.ok(nutritionProfileService.nutritionProfileFind(currentUser, trackerId));
	}
}