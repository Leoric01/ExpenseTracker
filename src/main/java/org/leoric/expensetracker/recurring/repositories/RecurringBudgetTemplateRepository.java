package org.leoric.expensetracker.recurring.repositories;

import org.leoric.expensetracker.recurring.models.RecurringBudgetTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface RecurringBudgetTemplateRepository extends JpaRepository<RecurringBudgetTemplate, UUID> {

	List<RecurringBudgetTemplate> findByExpenseTrackerId(UUID expenseTrackerId);

	List<RecurringBudgetTemplate> findByExpenseTrackerIdAndActiveTrue(UUID expenseTrackerId);

	List<RecurringBudgetTemplate> findByActiveTrueAndNextRunDateLessThanEqual(LocalDate date);

	void deleteByExpenseTrackerId(UUID expenseTrackerId);
}