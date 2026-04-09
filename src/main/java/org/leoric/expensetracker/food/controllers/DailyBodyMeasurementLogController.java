package org.leoric.expensetracker.food.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessService;
import org.leoric.expensetracker.food.dtos.DailyBodyMeasurementLogResponseDto;
import org.leoric.expensetracker.food.dtos.UpsertDailyBodyMeasurementLogRequestDto;
import org.leoric.expensetracker.food.services.interfaces.DailyBodyMeasurementLogService;
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
@RequestMapping("/api/daily-body-measurement-log")
@RequiredArgsConstructor
public class DailyBodyMeasurementLogController {

	private final DailyBodyMeasurementLogService dailyBodyMeasurementLogService;
	private final ExpenseTrackerAccessService expenseTrackerAccessService;

	@PutMapping("/{trackerId}")
	public ResponseEntity<DailyBodyMeasurementLogResponseDto> dailyBodyMeasurementLogUpsert(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@Valid @RequestBody UpsertDailyBodyMeasurementLogRequestDto request) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);

		return ResponseEntity.ok(dailyBodyMeasurementLogService.dailyBodyMeasurementLogUpsert(currentUser, trackerId, request));
	}

	@GetMapping("/{trackerId}/{logDate}")
	public ResponseEntity<DailyBodyMeasurementLogResponseDto> dailyBodyMeasurementLogFindByDate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate logDate) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);

		return ResponseEntity.ok(dailyBodyMeasurementLogService.dailyBodyMeasurementLogFindByDate(currentUser, trackerId, logDate));
	}

	@GetMapping("/{trackerId}")
	public ResponseEntity<Page<DailyBodyMeasurementLogResponseDto>> dailyBodyMeasurementLogFindAll(
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

		return ResponseEntity.ok(dailyBodyMeasurementLogService.dailyBodyMeasurementLogFindAll(currentUser, trackerId, from, to, pageable));
	}

	@DeleteMapping("/{trackerId}/{logDate}")
	public ResponseEntity<Void> dailyBodyMeasurementLogDeleteByDate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate logDate) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER
		);

		dailyBodyMeasurementLogService.dailyBodyMeasurementLogDeleteByDate(currentUser, trackerId, logDate);
		return ResponseEntity.noContent().build();
	}
}