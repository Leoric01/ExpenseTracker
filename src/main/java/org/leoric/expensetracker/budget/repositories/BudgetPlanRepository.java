package org.leoric.expensetracker.budget.repositories;

import org.leoric.expensetracker.budget.models.BudgetPlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BudgetPlanRepository extends JpaRepository<BudgetPlan, UUID> {

	void deleteByExpenseTrackerId(UUID expenseTrackerId);

	Page<BudgetPlan> findByExpenseTrackerId(UUID expenseTrackerId, Pageable pageable);

	Page<BudgetPlan> findByExpenseTrackerIdAndActiveTrue(UUID expenseTrackerId, Pageable pageable);

	@Query("""
			SELECT b FROM BudgetPlan b
			WHERE b.expenseTracker.id = :trackerId
			AND (
				LOWER(b.name) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(b.currencyCode) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(CAST(b.periodType AS string)) LIKE LOWER(CONCAT('%', :search, '%'))
				OR (b.category IS NOT NULL AND LOWER(b.category.name) LIKE LOWER(CONCAT('%', :search, '%')))
			)
			""")
	Page<BudgetPlan> findByExpenseTrackerIdWithSearch(
			@Param("trackerId") UUID trackerId,
			@Param("search") String search,
			Pageable pageable);

	@Query("""
			SELECT b FROM BudgetPlan b
			WHERE b.expenseTracker.id = :trackerId
			AND b.active = true
			AND (
				LOWER(b.name) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(b.currencyCode) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(CAST(b.periodType AS string)) LIKE LOWER(CONCAT('%', :search, '%'))
				OR (b.category IS NOT NULL AND LOWER(b.category.name) LIKE LOWER(CONCAT('%', :search, '%')))
			)
			""")
	Page<BudgetPlan> findByExpenseTrackerIdAndActiveTrueWithSearch(
			@Param("trackerId") UUID trackerId,
			@Param("search") String search,
			Pageable pageable);

	boolean existsByExpenseTrackerIdAndNameIgnoreCase(UUID expenseTrackerId, String name);
}