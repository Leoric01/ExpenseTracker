package org.leoric.expensetracker.category.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.category.dto.CategoryBulkExportResponseDto;
import org.leoric.expensetracker.category.dto.CreateCategoryBulkRequestDto;
import org.leoric.expensetracker.category.dto.CategoryResponseDto;
import org.leoric.expensetracker.category.dto.CreateCategoryRequestDto;
import org.leoric.expensetracker.category.dto.UpdateCategoryRequestDto;
import org.leoric.expensetracker.category.services.interfaces.CategoryService;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessService;
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
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_MEMBER;
import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_OWNER;

@RestController
@RequestMapping("/api/category")
@RequiredArgsConstructor
@Slf4j
public class CategoryController {

	private final CategoryService categoryService;
	private final ExpenseTrackerAccessService expenseTrackerAccessService;

	@PostMapping("/{trackerId}")
	public ResponseEntity<CategoryResponseDto> categoryCreate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@Valid @RequestBody CreateCategoryRequestDto request) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(categoryService.categoryCreate(currentUser, trackerId, request));
	}

	@PostMapping("/{trackerId}/bulk")
	public ResponseEntity<List<CategoryResponseDto>> categoryCreateBulk(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@Valid @RequestBody List<CreateCategoryBulkRequestDto> request) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(categoryService.categoryCreateBulk(currentUser, trackerId, request));
	}

	@GetMapping("/{trackerId}/export")
	public ResponseEntity<List<CategoryBulkExportResponseDto>> categoryExportBulk(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.ok(categoryService.categoryExportBulk(currentUser, trackerId));
	}

	@GetMapping("/{trackerId}")
	public ResponseEntity<Page<CategoryResponseDto>> categoryFindAll(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@RequestParam(required = false) String search,
			@ParameterObject Pageable pageable) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.ok(categoryService.categoryFindAll(currentUser, trackerId, search, pageable));
	}

	@GetMapping("/{trackerId}/active")
	public ResponseEntity<Page<CategoryResponseDto>> categoryFindAllActive(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@RequestParam(required = false) String search,
			@RequestParam(required = false) Instant dateFrom,
			@RequestParam(required = false) Instant dateTo,
			@ParameterObject Pageable pageable) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		LocalDate from = dateFrom != null ? dateFrom.atZone(ZoneOffset.UTC).toLocalDate() : null;
		LocalDate to = dateTo != null ? dateTo.atZone(ZoneOffset.UTC).toLocalDate() : null;
		log.debug("categoryFindAllActive — dateFrom(Instant)={}, dateTo(Instant)={}, from(LocalDate)={}, to(LocalDate)={}",
				dateFrom, dateTo, from, to);
		return ResponseEntity.ok(categoryService.categoryFindAllActive(currentUser, trackerId, search, from, to, pageable));
	}

	@GetMapping("/{trackerId}/{categoryId}")
	public ResponseEntity<CategoryResponseDto> categoryFindById(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID categoryId) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.ok(categoryService.categoryFindById(currentUser, trackerId, categoryId));
	}

	@PatchMapping("/{trackerId}/{categoryId}")
	public ResponseEntity<CategoryResponseDto> categoryUpdate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID categoryId,
			@Valid @RequestBody UpdateCategoryRequestDto request) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER);
		return ResponseEntity.ok(categoryService.categoryUpdate(currentUser, trackerId, categoryId, request));
	}

	@DeleteMapping("/{trackerId}/{categoryId}")
	public ResponseEntity<Void> categoryDeactivate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID categoryId,
			@RequestParam(defaultValue = "false") boolean cascade) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER);
		categoryService.categoryDeactivate(currentUser, trackerId, categoryId, cascade);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{trackerId}/{categoryId}/icon")
	public ResponseEntity<CategoryResponseDto> categoryUploadIcon(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID categoryId,
			@RequestParam("icon") MultipartFile icon,
			@RequestParam(value = "iconColor", required = false) String iconColor) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.ok(categoryService.categoryUploadIcon(currentUser, trackerId, categoryId, icon, iconColor));
	}

	@DeleteMapping("/{trackerId}/{categoryId}/icon")
	public ResponseEntity<CategoryResponseDto> categoryDeleteIcon(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID categoryId) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.ok(categoryService.categoryDeleteIcon(currentUser, trackerId, categoryId));
	}
}