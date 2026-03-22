package org.leoric.expensetracker.budget.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.budget.dto.BudgetPlanResponseDto;
import org.leoric.expensetracker.budget.dto.CreateBudgetPlanRequestDto;
import org.leoric.expensetracker.budget.dto.UpdateBudgetPlanRequestDto;
import org.leoric.expensetracker.budget.mapstruct.BudgetPlanMapper;
import org.leoric.expensetracker.budget.models.BudgetPlan;
import org.leoric.expensetracker.budget.repositories.BudgetPlanRepository;
import org.leoric.expensetracker.budget.services.interfaces.BudgetPlanService;
import org.leoric.expensetracker.category.models.Category;
import org.leoric.expensetracker.category.repositories.CategoryRepository;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.handler.exceptions.DuplicateBudgetPlanNameException;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class BudgetPlanServiceImpl implements BudgetPlanService {

	private final BudgetPlanRepository budgetPlanRepository;
	private final ExpenseTrackerRepository expenseTrackerRepository;
	private final CategoryRepository categoryRepository;
	private final BudgetPlanMapper budgetPlanMapper;

	@Override
	@Transactional
	public BudgetPlanResponseDto budgetPlanCreate(User currentUser, UUID trackerId, CreateBudgetPlanRequestDto request) {
		ExpenseTracker tracker = getTrackerOrThrow(trackerId);

		if (budgetPlanRepository.existsByExpenseTrackerIdAndNameIgnoreCase(trackerId, request.name())) {
			throw new DuplicateBudgetPlanNameException(
					"Budget plan with name '%s' already exists in this expense tracker".formatted(request.name()));
		}

		Category category = null;
		if (request.categoryId() != null) {
			category = getCategoryOrThrow(request.categoryId());
			assertCategoryBelongsToTracker(category, trackerId);
		}

		BudgetPlan budgetPlan = BudgetPlan.builder()
				.expenseTracker(tracker)
				.name(request.name())
				.amount(request.amount())
				.currencyCode(request.currencyCode().toUpperCase())
				.periodType(request.periodType())
				.validFrom(request.validFrom())
				.validTo(request.validTo())
				.category(category)
				.build();

		budgetPlan = budgetPlanRepository.save(budgetPlan);
		log.info("User {} created budget plan '{}' in tracker '{}'",
				currentUser.getEmail(), budgetPlan.getName(), tracker.getName());

		return budgetPlanMapper.toResponse(budgetPlan);
	}

	@Override
	@Transactional(readOnly = true)
	public BudgetPlanResponseDto budgetPlanFindById(User currentUser, UUID trackerId, UUID budgetPlanId) {
		BudgetPlan budgetPlan = getBudgetPlanOrThrow(budgetPlanId);
		assertBudgetPlanBelongsToTracker(budgetPlan, trackerId);
		return budgetPlanMapper.toResponse(budgetPlan);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<BudgetPlanResponseDto> budgetPlanFindAll(User currentUser, UUID trackerId, String search, Pageable pageable) {
		if (search != null && !search.isBlank()) {
			return budgetPlanRepository.findByExpenseTrackerIdWithSearch(trackerId, search, pageable)
					.map(budgetPlanMapper::toResponse);
		}
		return budgetPlanRepository.findByExpenseTrackerId(trackerId, pageable)
				.map(budgetPlanMapper::toResponse);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<BudgetPlanResponseDto> budgetPlanFindAllActive(User currentUser, UUID trackerId, String search, Pageable pageable) {
		if (search != null && !search.isBlank()) {
			return budgetPlanRepository.findByExpenseTrackerIdAndActiveTrueWithSearch(trackerId, search, pageable)
					.map(budgetPlanMapper::toResponse);
		}
		return budgetPlanRepository.findByExpenseTrackerIdAndActiveTrue(trackerId, pageable)
				.map(budgetPlanMapper::toResponse);
	}

	@Override
	@Transactional
	public BudgetPlanResponseDto budgetPlanUpdate(User currentUser, UUID trackerId, UUID budgetPlanId, UpdateBudgetPlanRequestDto request) {
		BudgetPlan budgetPlan = getBudgetPlanOrThrow(budgetPlanId);
		assertBudgetPlanBelongsToTracker(budgetPlan, trackerId);

		if (request.categoryId() != null) {
			Category category = getCategoryOrThrow(request.categoryId());
			assertCategoryBelongsToTracker(category, trackerId);
			budgetPlan.setCategory(category);
		}

		budgetPlanMapper.updateFromDto(request, budgetPlan);

		if (request.currencyCode() != null) {
			budgetPlan.setCurrencyCode(request.currencyCode().toUpperCase());
		}

		budgetPlan = budgetPlanRepository.save(budgetPlan);
		log.info("User {} updated budget plan '{}' in tracker '{}'",
				currentUser.getEmail(), budgetPlan.getName(), budgetPlan.getExpenseTracker().getName());

		return budgetPlanMapper.toResponse(budgetPlan);
	}

	@Override
	@Transactional
	public void budgetPlanDeactivate(User currentUser, UUID trackerId, UUID budgetPlanId) {
		BudgetPlan budgetPlan = getBudgetPlanOrThrow(budgetPlanId);
		assertBudgetPlanBelongsToTracker(budgetPlan, trackerId);

		if (!budgetPlan.isActive()) {
			throw new OperationNotPermittedException("Budget plan is already deactivated");
		}

		budgetPlan.setActive(false);
		budgetPlanRepository.save(budgetPlan);
		log.info("User {} deactivated budget plan '{}' in tracker '{}'",
				currentUser.getEmail(), budgetPlan.getName(), budgetPlan.getExpenseTracker().getName());
	}

	// ── Helpers ──

	private ExpenseTracker getTrackerOrThrow(UUID trackerId) {
		return expenseTrackerRepository.findById(trackerId)
				.orElseThrow(() -> new EntityNotFoundException("Expense tracker not found"));
	}

	private BudgetPlan getBudgetPlanOrThrow(UUID budgetPlanId) {
		return budgetPlanRepository.findById(budgetPlanId)
				.orElseThrow(() -> new EntityNotFoundException("Budget plan not found"));
	}

	private Category getCategoryOrThrow(UUID categoryId) {
		return categoryRepository.findById(categoryId)
				.orElseThrow(() -> new EntityNotFoundException("Category not found"));
	}

	private void assertBudgetPlanBelongsToTracker(BudgetPlan budgetPlan, UUID trackerId) {
		if (!budgetPlan.getExpenseTracker().getId().equals(trackerId)) {
			throw new EntityNotFoundException("Budget plan not found in this expense tracker");
		}
	}

	private void assertCategoryBelongsToTracker(Category category, UUID trackerId) {
		if (!category.getExpenseTracker().getId().equals(trackerId)) {
			throw new EntityNotFoundException("Category not found in this expense tracker");
		}
	}
}