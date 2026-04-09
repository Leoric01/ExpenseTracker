package org.leoric.expensetracker.food.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessService;
import org.leoric.expensetracker.food.dtos.DailyCheckinResponseDto;
import org.leoric.expensetracker.food.dtos.UpsertDailyCheckinRequestDto;
import org.leoric.expensetracker.food.services.interfaces.DailyCheckinService;
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
@RequestMapping("/api/daily-checkin")
@RequiredArgsConstructor
public class DailyCheckinController {

	private final DailyCheckinService dailyCheckinService;
	private final ExpenseTrackerAccessService expenseTrackerAccessService;

	@PutMapping("/{trackerId}")
	public ResponseEntity<DailyCheckinResponseDto> dailyCheckinUpsert(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@Valid @RequestBody UpsertDailyCheckinRequestDto request) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);

		return ResponseEntity.ok(dailyCheckinService.dailyCheckinUpsert(currentUser, trackerId, request));
	}
}