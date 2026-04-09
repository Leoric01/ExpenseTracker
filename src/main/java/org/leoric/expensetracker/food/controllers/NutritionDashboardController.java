package org.leoric.expensetracker.food.controllers;

import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessService;
import org.leoric.expensetracker.food.dtos.NutritionDashboardResponseDto;
import org.leoric.expensetracker.food.dtos.NutritionSummaryResponseDto;
import org.leoric.expensetracker.food.services.interfaces.NutritionDashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_MEMBER;
import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_OWNER;

@RestController
@RequestMapping("/api/nutrition-dashboard")
@RequiredArgsConstructor
public class NutritionDashboardController {

	private final NutritionDashboardService nutritionDashboardService;
	private final ExpenseTrackerAccessService expenseTrackerAccessService;

	@GetMapping("/{trackerId}")
	public ResponseEntity<NutritionDashboardResponseDto> nutritionDashboard(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);

		if (from == null) {
			from = LocalDate.now(ZoneOffset.UTC).minusDays(90);
		}
		if (to == null) {
			to = LocalDate.now(ZoneOffset.UTC);
		}

		return ResponseEntity.ok(nutritionDashboardService.nutritionDashboard(currentUser, trackerId, from, to));
	}

	@GetMapping("/{trackerId}/{goalPlanId}/summary")
	public ResponseEntity<NutritionSummaryResponseDto> nutritionSummary(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID goalPlanId) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);

		return ResponseEntity.ok(nutritionDashboardService.nutritionSummary(currentUser, trackerId, goalPlanId));
	}
}