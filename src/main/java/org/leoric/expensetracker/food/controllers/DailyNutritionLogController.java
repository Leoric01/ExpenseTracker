package org.leoric.expensetracker.food.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessService;
import org.leoric.expensetracker.food.dtos.DailyNutritionLogResponseDto;
import org.leoric.expensetracker.food.dtos.UpsertDailyNutritionLogRequestDto;
import org.leoric.expensetracker.food.services.interfaces.DailyNutritionLogService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_MEMBER;
import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_OWNER;

@RestController
@RequestMapping("/api/daily-nutrition-log")
@RequiredArgsConstructor
public class DailyNutritionLogController {

	private final DailyNutritionLogService dailyNutritionLogService;
	private final ExpenseTrackerAccessService expenseTrackerAccessService;

	@PutMapping("/{trackerId}")
	public ResponseEntity<DailyNutritionLogResponseDto> dailyNutritionLogUpsert(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@Valid @RequestBody UpsertDailyNutritionLogRequestDto request) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);

		return ResponseEntity.ok(dailyNutritionLogService.dailyNutritionLogUpsert(currentUser, trackerId, request));
	}

	@GetMapping("/{trackerId}/{logDate}")
	public ResponseEntity<DailyNutritionLogResponseDto> dailyNutritionLogFindByDate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate logDate) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);

		return ResponseEntity.ok(dailyNutritionLogService.dailyNutritionLogFindByDate(currentUser, trackerId, logDate));
	}

	@GetMapping("/{trackerId}")
	public ResponseEntity<Page<DailyNutritionLogResponseDto>> dailyNutritionLogFindAll(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@ParameterObject Pageable pageable) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);

		return ResponseEntity.ok(dailyNutritionLogService.dailyNutritionLogFindAll(currentUser, trackerId, from, to, pageable));
	}

	@DeleteMapping("/{trackerId}/{logDate}")
	public ResponseEntity<Void> dailyNutritionLogDeleteByDate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate logDate) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER
		);

		dailyNutritionLogService.dailyNutritionLogDeleteByDate(currentUser, trackerId, logDate);
		return ResponseEntity.noContent().build();
	}
}