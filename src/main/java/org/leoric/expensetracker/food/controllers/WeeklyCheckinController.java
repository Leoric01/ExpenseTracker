package org.leoric.expensetracker.food.controllers;

import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessService;
import org.leoric.expensetracker.food.dtos.WeeklyCheckinResponseDto;
import org.leoric.expensetracker.food.services.interfaces.WeeklyCheckinService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_MEMBER;
import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_OWNER;

@RestController
@RequestMapping("/api/weekly-checkin")
@RequiredArgsConstructor
public class WeeklyCheckinController {

	private final WeeklyCheckinService weeklyCheckinService;
	private final ExpenseTrackerAccessService expenseTrackerAccessService;

	@PostMapping("/{trackerId}/{goalPlanId}/{weekIndex}")
	public ResponseEntity<WeeklyCheckinResponseDto> weeklyCheckinGenerate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID goalPlanId,
			@PathVariable Integer weekIndex) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);

		return ResponseEntity.status(HttpStatus.CREATED)
				.body(weeklyCheckinService.weeklyCheckinGenerate(currentUser, trackerId, goalPlanId, weekIndex));
	}

	@PostMapping("/{trackerId}/{goalPlanId}/current")
	public ResponseEntity<WeeklyCheckinResponseDto> weeklyCheckinGenerateForDate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID goalPlanId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);

		LocalDate effectiveDate = date != null ? date : LocalDate.now();
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(weeklyCheckinService.weeklyCheckinGenerateForDate(currentUser, trackerId, goalPlanId, effectiveDate));
	}

	@GetMapping("/{trackerId}/{goalPlanId}/{weekIndex}")
	public ResponseEntity<WeeklyCheckinResponseDto> weeklyCheckinFindByWeekIndex(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID goalPlanId,
			@PathVariable Integer weekIndex) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);

		return ResponseEntity.ok(weeklyCheckinService.weeklyCheckinFindByWeekIndex(currentUser, trackerId, goalPlanId, weekIndex));
	}

	@GetMapping("/{trackerId}/{goalPlanId}")
	public ResponseEntity<Page<WeeklyCheckinResponseDto>> weeklyCheckinFindAll(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID goalPlanId,
			@ParameterObject Pageable pageable) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);

		return ResponseEntity.ok(weeklyCheckinService.weeklyCheckinFindAll(currentUser, trackerId, goalPlanId, pageable));
	}
}