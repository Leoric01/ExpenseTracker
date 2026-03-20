package org.leoric.expensetracker.recurring.repositories;

import org.leoric.expensetracker.recurring.models.RecurringTransactionTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface RecurringTransactionTemplateRepository extends JpaRepository<RecurringTransactionTemplate, UUID> {

	List<RecurringTransactionTemplate> findByExpenseTrackerId(UUID expenseTrackerId);

	List<RecurringTransactionTemplate> findByExpenseTrackerIdAndActiveTrue(UUID expenseTrackerId);

	List<RecurringTransactionTemplate> findByActiveTrueAndNextRunDateLessThanEqual(LocalDate date);

	void deleteByExpenseTrackerId(UUID expenseTrackerId);
}