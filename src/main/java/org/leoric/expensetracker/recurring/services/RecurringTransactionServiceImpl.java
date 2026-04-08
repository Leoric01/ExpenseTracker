package org.leoric.expensetracker.recurring.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.budget.models.constants.PeriodType;
import org.leoric.expensetracker.category.models.Category;
import org.leoric.expensetracker.category.models.constants.CategoryKind;
import org.leoric.expensetracker.category.repositories.CategoryRepository;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.leoric.expensetracker.holding.models.Holding;
import org.leoric.expensetracker.holding.repositories.HoldingRepository;
import org.leoric.expensetracker.recurring.dto.CreateRecurringTransactionRequestDto;
import org.leoric.expensetracker.recurring.dto.RecurringTransactionResponseDto;
import org.leoric.expensetracker.recurring.dto.UpdateRecurringTransactionRequestDto;
import org.leoric.expensetracker.recurring.mapstruct.RecurringTransactionMapper;
import org.leoric.expensetracker.recurring.models.RecurringTransactionTemplate;
import org.leoric.expensetracker.recurring.repositories.RecurringTransactionTemplateRepository;
import org.leoric.expensetracker.recurring.services.interfaces.RecurringTransactionService;
import org.leoric.expensetracker.transaction.models.Transaction;
import org.leoric.expensetracker.transaction.models.constants.TransactionStatus;
import org.leoric.expensetracker.transaction.models.constants.TransactionType;
import org.leoric.expensetracker.transaction.repositories.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class RecurringTransactionServiceImpl implements RecurringTransactionService {

	private final RecurringTransactionTemplateRepository templateRepository;
	private final TransactionRepository transactionRepository;
	private final ExpenseTrackerRepository expenseTrackerRepository;
	private final HoldingRepository holdingRepository;
	private final CategoryRepository categoryRepository;
	private final RecurringTransactionMapper mapper;

	@Override
	@Transactional
	public RecurringTransactionResponseDto recurringTransactionCreate(User currentUser, UUID trackerId, CreateRecurringTransactionRequestDto request) {
		ExpenseTracker tracker = getTrackerOrThrow(trackerId);

		if (request.transactionType() == TransactionType.TRANSFER || request.transactionType() == TransactionType.BALANCE_ADJUSTMENT) {
			throw new OperationNotPermittedException("Recurring templates only support INCOME and EXPENSE transaction types");
		}

		Holding holding = getHoldingOrThrow(request.holdingId());
		assertHoldingBelongsToTracker(holding, trackerId);

		Category category = null;
		if (request.categoryId() != null) {
			category = getCategoryOrThrow(request.categoryId());
			assertCategoryBelongsToTracker(category, trackerId);
			assertCategoryMatchesTransactionType(category, request.transactionType());
		}

		RecurringTransactionTemplate template = RecurringTransactionTemplate.builder()
				.expenseTracker(tracker)
				.transactionType(request.transactionType())
				.holding(holding)
				.category(category)
				.amount(request.amount())
				.currencyCode(holding.getAsset().getCode())
				.description(request.description())
				.note(request.note())
				.periodType(request.periodType())
				.intervalValue(request.intervalValue() != null ? request.intervalValue() : 1)
				.startDate(request.startDate())
				.endDate(request.endDate())
				.nextRunDate(request.startDate())
				.build();

		template = templateRepository.save(template);
		log.info("User {} created recurring transaction template '{}' in tracker '{}'",
				currentUser.getEmail(), template.getId(), tracker.getName());

		// Immediately generate transactions for periods that are already due
		generateDueTransactions(template);

		return mapper.toResponse(template);
	}

	@Override
	@Transactional(readOnly = true)
	public RecurringTransactionResponseDto recurringTransactionFindById(User currentUser, UUID trackerId, UUID templateId) {
		RecurringTransactionTemplate template = getTemplateOrThrow(templateId);
		assertTemplateBelongsToTracker(template, trackerId);
		return mapper.toResponse(template);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<RecurringTransactionResponseDto> recurringTransactionFindAll(User currentUser, UUID trackerId, String search, Pageable pageable) {
		if (search != null && !search.isBlank()) {
			return templateRepository.findByExpenseTrackerIdWithSearch(trackerId, search, pageable)
					.map(mapper::toResponse);
		}
		return templateRepository.findByExpenseTrackerId(trackerId, pageable)
				.map(mapper::toResponse);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<RecurringTransactionResponseDto> recurringTransactionFindAllActive(User currentUser, UUID trackerId, String search, Pageable pageable) {
		if (search != null && !search.isBlank()) {
			return templateRepository.findByExpenseTrackerIdAndActiveTrueWithSearch(trackerId, search, pageable)
					.map(mapper::toResponse);
		}
		return templateRepository.findByExpenseTrackerIdAndActiveTrue(trackerId, pageable)
				.map(mapper::toResponse);
	}

	@Override
	@Transactional
	public RecurringTransactionResponseDto recurringTransactionUpdate(User currentUser, UUID trackerId, UUID templateId, UpdateRecurringTransactionRequestDto request) {
		RecurringTransactionTemplate template = getTemplateOrThrow(templateId);
		assertTemplateBelongsToTracker(template, trackerId);

		if (request.holdingId() != null) {
			Holding holding = getHoldingOrThrow(request.holdingId());
			assertHoldingBelongsToTracker(holding, trackerId);
			template.setHolding(holding);
		}

		if (request.categoryId() != null) {
			Category category = getCategoryOrThrow(request.categoryId());
			assertCategoryBelongsToTracker(category, trackerId);
			assertCategoryMatchesTransactionType(category, template.getTransactionType());
			template.setCategory(category);
		}

		mapper.updateFromDto(request, template);

		if (request.currencyCode() != null) {
			template.setCurrencyCode(request.currencyCode().toUpperCase());
		}

		template = templateRepository.save(template);
		log.info("User {} updated recurring transaction template '{}' in tracker '{}'",
				currentUser.getEmail(), template.getId(), template.getExpenseTracker().getName());

		return mapper.toResponse(template);
	}

	@Override
	@Transactional
	public void recurringTransactionDeactivate(User currentUser, UUID trackerId, UUID templateId) {
		RecurringTransactionTemplate template = getTemplateOrThrow(templateId);
		assertTemplateBelongsToTracker(template, trackerId);

		if (!template.isActive()) {
			throw new OperationNotPermittedException("Recurring transaction template is already deactivated");
		}

		template.setActive(false);
		templateRepository.save(template);
		log.info("User {} deactivated recurring transaction template '{}' in tracker '{}'",
				currentUser.getEmail(), template.getId(), template.getExpenseTracker().getName());
	}

	// ── Helpers ──

	private ExpenseTracker getTrackerOrThrow(UUID trackerId) {
		return expenseTrackerRepository.findById(trackerId)
				.orElseThrow(() -> new EntityNotFoundException("Expense tracker not found"));
	}

	private RecurringTransactionTemplate getTemplateOrThrow(UUID templateId) {
		return templateRepository.findById(templateId)
				.orElseThrow(() -> new EntityNotFoundException("Recurring transaction template not found"));
	}

	private Holding getHoldingOrThrow(UUID holdingId) {
		return holdingRepository.findById(holdingId)
				.orElseThrow(() -> new EntityNotFoundException("Holding not found"));
	}

	private Category getCategoryOrThrow(UUID categoryId) {
		return categoryRepository.findById(categoryId)
				.orElseThrow(() -> new EntityNotFoundException("Category not found"));
	}

	private void assertTemplateBelongsToTracker(RecurringTransactionTemplate template, UUID trackerId) {
		if (!template.getExpenseTracker().getId().equals(trackerId)) {
			throw new EntityNotFoundException("Recurring transaction template not found in this expense tracker");
		}
	}

	private void assertHoldingBelongsToTracker(Holding holding, UUID trackerId) {
		if (!holding.getAccount().getInstitution().getExpenseTracker().getId().equals(trackerId)) {
			throw new EntityNotFoundException("Holding not found in this expense tracker");
		}
	}

	private void assertCategoryBelongsToTracker(Category category, UUID trackerId) {
		if (!category.getExpenseTracker().getId().equals(trackerId)) {
			throw new EntityNotFoundException("Category not found in this expense tracker");
		}
	}

	private void assertCategoryMatchesTransactionType(Category category, TransactionType type) {
		CategoryKind expectedKind = type == TransactionType.INCOME ? CategoryKind.INCOME : CategoryKind.EXPENSE;
		if (category.getCategoryKind() != expectedKind) {
			throw new OperationNotPermittedException(
					"Category '%s' is of kind %s but transaction type is %s".formatted(
							category.getName(), category.getCategoryKind(), type));
		}
	}

	private void generateDueTransactions(RecurringTransactionTemplate template) {
		LocalDate today = LocalDate.now();

		while (template.getNextRunDate() != null && !template.getNextRunDate().isAfter(today)) {
			// Respect endDate
			if (template.getEndDate() != null && today.isAfter(template.getEndDate())) {
				template.setActive(false);
				templateRepository.save(template);
				log.info("Deactivated expired recurring transaction template '{}' during initial catch-up (endDate {})",
						template.getId(), template.getEndDate());
				return;
			}

			Holding holding = template.getHolding();
			if (holding == null || !holding.isActive()) {
				log.warn("Skipping catch-up for recurring transaction template '{}' — holding is null or deactivated",
						template.getId());
				return;
			}

			// Apply balance effect
			if (template.getTransactionType() == TransactionType.INCOME) {
				holding.setCurrentAmount(holding.getCurrentAmount() + template.getAmount());
			} else if (template.getTransactionType() == TransactionType.EXPENSE) {
				holding.setCurrentAmount(holding.getCurrentAmount() - template.getAmount());
			}
			holdingRepository.save(holding);

			Transaction transaction = Transaction.builder()
					.expenseTracker(template.getExpenseTracker())
					.transactionType(template.getTransactionType())
					.status(TransactionStatus.COMPLETED)
					.holding(holding)
					.category(template.getCategory())
					.amount(template.getAmount())
					.currencyCode(template.getCurrencyCode())
					.transactionDate(Instant.now())
					.description(template.getDescription())
					.note(template.getNote())
					.build();

			transactionRepository.save(transaction);
			log.info("Generated initial transaction from recurring template '{}' ({} {} {})",
					template.getId(), template.getTransactionType(), template.getAmount(), template.getCurrencyCode());

			LocalDate nextRun = computeNextRunDate(template.getNextRunDate(), template.getPeriodType(), template.getIntervalValue());

			if (template.getEndDate() != null && nextRun.isAfter(template.getEndDate())) {
				template.setActive(false);
				template.setNextRunDate(nextRun);
				templateRepository.save(template);
				log.info("Deactivated recurring transaction template '{}' after catch-up (next run {} past endDate {})",
						template.getId(), nextRun, template.getEndDate());
				return;
			}

			template.setNextRunDate(nextRun);
			templateRepository.save(template);
		}
	}

	private LocalDate computeNextRunDate(LocalDate current, PeriodType periodType, int interval) {
		return switch (periodType) {
			case DAILY -> current.plusDays(interval);
			case WEEKLY -> current.plusWeeks(interval);
			case MONTHLY -> current.plusMonths(interval);
			case QUARTERLY -> current.plusMonths(3L * interval);
			case YEARLY -> current.plusYears(interval);
		};
	}
}