package org.leoric.expensetracker.recurring.repositories;

import org.leoric.expensetracker.recurring.models.RecurringTransactionTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface RecurringTransactionTemplateRepository extends JpaRepository<RecurringTransactionTemplate, UUID> {

	List<RecurringTransactionTemplate> findByExpenseTrackerId(UUID expenseTrackerId);

	List<RecurringTransactionTemplate> findByActiveTrueAndNextRunDateLessThanEqual(LocalDate date);

	void deleteByExpenseTrackerId(UUID expenseTrackerId);

	Page<RecurringTransactionTemplate> findByExpenseTrackerId(UUID expenseTrackerId, Pageable pageable);

	Page<RecurringTransactionTemplate> findByExpenseTrackerIdAndActiveTrue(UUID expenseTrackerId, Pageable pageable);

	@Query("""
			SELECT r FROM RecurringTransactionTemplate r
			WHERE r.expenseTracker.id = :trackerId
			AND (
				LOWER(r.description) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(r.note) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(r.currencyCode) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(CAST(r.transactionType AS string)) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(CAST(r.periodType AS string)) LIKE LOWER(CONCAT('%', :search, '%'))
				OR (r.holding IS NOT NULL AND LOWER(r.holding.account.name) LIKE LOWER(CONCAT('%', :search, '%')))
				OR (r.category IS NOT NULL AND LOWER(r.category.name) LIKE LOWER(CONCAT('%', :search, '%')))
			)
			""")
	Page<RecurringTransactionTemplate> findByExpenseTrackerIdWithSearch(
			@Param("trackerId") UUID trackerId,
			@Param("search") String search,
			Pageable pageable);

	@Query("""
			SELECT r FROM RecurringTransactionTemplate r
			WHERE r.expenseTracker.id = :trackerId
			AND r.active = true
			AND (
				LOWER(r.description) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(r.note) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(r.currencyCode) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(CAST(r.transactionType AS string)) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(CAST(r.periodType AS string)) LIKE LOWER(CONCAT('%', :search, '%'))
				OR (r.holding IS NOT NULL AND LOWER(r.holding.account.name) LIKE LOWER(CONCAT('%', :search, '%')))
				OR (r.category IS NOT NULL AND LOWER(r.category.name) LIKE LOWER(CONCAT('%', :search, '%')))
			)
			""")
	Page<RecurringTransactionTemplate> findByExpenseTrackerIdAndActiveTrueWithSearch(
			@Param("trackerId") UUID trackerId,
			@Param("search") String search,
			Pageable pageable);
}