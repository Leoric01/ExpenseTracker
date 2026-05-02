package org.leoric.expensetracker.recurring.repositories;

import org.leoric.expensetracker.recurring.models.RecurringBudgetTemplate;
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
public interface RecurringBudgetTemplateRepository extends JpaRepository<RecurringBudgetTemplate, UUID> {

	List<RecurringBudgetTemplate> findByExpenseTrackerId(UUID expenseTrackerId);

	List<RecurringBudgetTemplate> findByExpenseTrackerIdAndActiveTrue(UUID expenseTrackerId);

	List<RecurringBudgetTemplate> findByActiveTrueAndNextRunDateLessThanEqual(LocalDate date);

	List<RecurringBudgetTemplate> findByExpenseTrackerIdAndActiveTrueAndNextRunDateLessThanEqual(UUID expenseTrackerId, LocalDate date);

	void deleteByExpenseTrackerId(UUID expenseTrackerId);

	Page<RecurringBudgetTemplate> findByExpenseTrackerId(UUID expenseTrackerId, Pageable pageable);

	Page<RecurringBudgetTemplate> findByExpenseTrackerIdAndActiveTrue(UUID expenseTrackerId, Pageable pageable);

	@Query("""
			SELECT r FROM RecurringBudgetTemplate r
			WHERE r.expenseTracker.id = :trackerId
			AND (
				LOWER(r.name) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(r.currencyCode) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(CAST(r.periodType AS string)) LIKE LOWER(CONCAT('%', :search, '%'))
				OR (r.category IS NOT NULL AND LOWER(r.category.name) LIKE LOWER(CONCAT('%', :search, '%')))
			)
			""")
	Page<RecurringBudgetTemplate> findByExpenseTrackerIdWithSearch(
			@Param("trackerId") UUID trackerId,
			@Param("search") String search,
			Pageable pageable);

	@Query("""
			SELECT r FROM RecurringBudgetTemplate r
			WHERE r.expenseTracker.id = :trackerId
			AND r.active = true
			AND (
				LOWER(r.name) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(r.currencyCode) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(CAST(r.periodType AS string)) LIKE LOWER(CONCAT('%', :search, '%'))
				OR (r.category IS NOT NULL AND LOWER(r.category.name) LIKE LOWER(CONCAT('%', :search, '%')))
			)
			""")
	Page<RecurringBudgetTemplate> findByExpenseTrackerIdAndActiveTrueWithSearch(
			@Param("trackerId") UUID trackerId,
			@Param("search") String search,
			Pageable pageable);
}