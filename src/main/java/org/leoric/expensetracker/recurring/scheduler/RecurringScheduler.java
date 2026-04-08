package org.leoric.expensetracker.recurring.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.budget.models.BudgetPlan;
import org.leoric.expensetracker.budget.models.constants.PeriodType;
import org.leoric.expensetracker.budget.repositories.BudgetPlanRepository;
import org.leoric.expensetracker.holding.models.Holding;
import org.leoric.expensetracker.holding.repositories.HoldingRepository;
import org.leoric.expensetracker.recurring.models.RecurringBudgetTemplate;
import org.leoric.expensetracker.recurring.models.RecurringTransactionTemplate;
import org.leoric.expensetracker.recurring.repositories.RecurringBudgetTemplateRepository;
import org.leoric.expensetracker.recurring.repositories.RecurringTransactionTemplateRepository;
import org.leoric.expensetracker.transaction.models.Transaction;
import org.leoric.expensetracker.transaction.models.constants.TransactionStatus;
import org.leoric.expensetracker.transaction.models.constants.TransactionType;
import org.leoric.expensetracker.transaction.repositories.TransactionRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class RecurringScheduler {

	private final RecurringTransactionTemplateRepository recurringTransactionRepo;
	private final RecurringBudgetTemplateRepository recurringBudgetRepo;
	private final TransactionRepository transactionRepository;
	private final BudgetPlanRepository budgetPlanRepository;
	private final HoldingRepository holdingRepository;

	@Scheduled(cron = "0 0 1 * * *") // Every day at 01:00
	public void processRecurringItems() {
		log.info("Starting recurring items processing");
		processRecurringTransactions();
		processRecurringBudgets();
		log.info("Finished recurring items processing");
	}

	@Transactional
	public void processRecurringTransactions() {
		LocalDate today = LocalDate.now();
		List<RecurringTransactionTemplate> templates =
				recurringTransactionRepo.findByActiveTrueAndNextRunDateLessThanEqual(today);

		log.info("Found {} recurring transaction templates to process", templates.size());

		for (RecurringTransactionTemplate template : templates) {
			try {
				processTransactionTemplate(template, today);
			} catch (Exception e) {
				log.error("Failed to process recurring transaction template '{}' (tracker '{}')",
						template.getId(), template.getExpenseTracker().getName(), e);
			}
		}
	}

	@Transactional
	public void processRecurringBudgets() {
		LocalDate today = LocalDate.now();
		List<RecurringBudgetTemplate> templates =
				recurringBudgetRepo.findByActiveTrueAndNextRunDateLessThanEqual(today);

		log.info("Found {} recurring budget templates to process", templates.size());

		for (RecurringBudgetTemplate template : templates) {
			try {
				processBudgetTemplate(template, today);
			} catch (Exception e) {
				log.error("Failed to process recurring budget template '{}' (tracker '{}')",
						template.getId(), template.getExpenseTracker().getName(), e);
			}
		}
	}

	private void processTransactionTemplate(RecurringTransactionTemplate template, LocalDate today) {
		// If past endDate, deactivate
		if (template.getEndDate() != null && today.isAfter(template.getEndDate())) {
			template.setActive(false);
			recurringTransactionRepo.save(template);
			log.info("Deactivated expired recurring transaction template '{}' (endDate {})",
					template.getId(), template.getEndDate());
			return;
		}

		// Check holding is still active
		Holding holding = template.getHolding();
		if (holding == null || !holding.isActive()) {
			log.warn("Skipping recurring transaction template '{}' — holding is null or deactivated", template.getId());
			return;
		}

		// Apply balance effect
		if (template.getTransactionType() == TransactionType.INCOME) {
			holding.setCurrentAmount(holding.getCurrentAmount() + template.getAmount());
		} else if (template.getTransactionType() == TransactionType.EXPENSE) {
			holding.setCurrentAmount(holding.getCurrentAmount() - template.getAmount());
		}
		holdingRepository.save(holding);

		// Create transaction
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

		// Advance nextRunDate
		LocalDate nextRun = computeNextRunDate(template.getNextRunDate(), template.getPeriodType(), template.getIntervalValue());

		// If next run would be past endDate, deactivate after this run
		if (template.getEndDate() != null && nextRun.isAfter(template.getEndDate())) {
			template.setActive(false);
			template.setNextRunDate(nextRun);
			recurringTransactionRepo.save(template);
			log.info("Processed and deactivated recurring transaction template '{}' (next run {} past endDate {})",
					template.getId(), nextRun, template.getEndDate());
		} else {
			template.setNextRunDate(nextRun);
			recurringTransactionRepo.save(template);
			log.info("Processed recurring transaction template '{}', next run: {}", template.getId(), nextRun);
		}
	}

	private void processBudgetTemplate(RecurringBudgetTemplate template, LocalDate today) {
		// If past endDate, deactivate
		if (template.getEndDate() != null && today.isAfter(template.getEndDate())) {
			template.setActive(false);
			recurringBudgetRepo.save(template);
			log.info("Deactivated expired recurring budget template '{}' (endDate {})",
					template.getId(), template.getEndDate());
			return;
		}

		// Compute validFrom/validTo for the generated budget plan
		LocalDate planValidFrom = template.getNextRunDate();
		LocalDate planValidTo = computeNextRunDate(planValidFrom, template.getPeriodType(), template.getIntervalValue()).minusDays(1);

		// Create budget plan
		BudgetPlan plan = BudgetPlan.builder()
				.expenseTracker(template.getExpenseTracker())
				.category(template.getCategory())
				.name(template.getName())
				.amount(template.getAmount())
				.currencyCode(template.getCurrencyCode())
				.periodType(template.getPeriodType())
				.validFrom(planValidFrom)
				.validTo(planValidTo)
				.build();

		budgetPlanRepository.save(plan);

		// Advance nextRunDate
		LocalDate nextRun = computeNextRunDate(template.getNextRunDate(), template.getPeriodType(), template.getIntervalValue());

		if (template.getEndDate() != null && nextRun.isAfter(template.getEndDate())) {
			template.setActive(false);
			template.setNextRunDate(nextRun);
			recurringBudgetRepo.save(template);
			log.info("Processed and deactivated recurring budget template '{}' (next run {} past endDate {})",
					template.getId(), nextRun, template.getEndDate());
		} else {
			template.setNextRunDate(nextRun);
			recurringBudgetRepo.save(template);
			log.info("Processed recurring budget template '{}', next run: {}", template.getId(), nextRun);
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