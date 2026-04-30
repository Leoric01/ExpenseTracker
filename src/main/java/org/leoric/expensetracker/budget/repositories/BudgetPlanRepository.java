package org.leoric.expensetracker.budget.repositories;

import org.leoric.expensetracker.budget.models.BudgetPlan;
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
public interface BudgetPlanRepository extends JpaRepository<BudgetPlan, UUID> {

	void deleteByExpenseTrackerId(UUID expenseTrackerId);

	Page<BudgetPlan> findByExpenseTrackerId(UUID expenseTrackerId, Pageable pageable);

	List<BudgetPlan> findByExpenseTrackerIdAndActiveTrue(UUID expenseTrackerId);

	@Query("""
			SELECT b FROM BudgetPlan b
			WHERE b.expenseTracker.id = :trackerId
			AND b.active = true
			AND b.validFrom <= :today
			AND (b.validTo IS NULL OR b.validTo >= :today)
			""")
	Page<BudgetPlan> findCurrentActiveByExpenseTrackerId(
			@Param("trackerId") UUID trackerId,
			@Param("today") LocalDate today,
			Pageable pageable);

	@Query("""
			SELECT b FROM BudgetPlan b
			WHERE b.expenseTracker.id = :trackerId
			AND b.active = true
			AND b.validFrom <= :today
			AND (b.validTo IS NULL OR b.validTo >= :today)
			AND (
				LOWER(b.name) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(b.currencyCode) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(CAST(b.periodType AS string)) LIKE LOWER(CONCAT('%', :search, '%'))
				OR (b.category IS NOT NULL AND LOWER(b.category.name) LIKE LOWER(CONCAT('%', :search, '%')))
			)
			""")
	Page<BudgetPlan> findCurrentActiveByExpenseTrackerIdWithSearch(
			@Param("trackerId") UUID trackerId,
			@Param("today") LocalDate today,
			@Param("search") String search,
			Pageable pageable);

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

	boolean existsByExpenseTrackerIdAndNameIgnoreCase(UUID expenseTrackerId, String name);

	@Query("""
			SELECT b FROM BudgetPlan b
			WHERE b.recurringBudgetTemplate.id = :templateId
			AND b.active = true
			AND b.validFrom <= :today
			AND (b.validTo IS NULL OR b.validTo >= :today)
			""")
	List<BudgetPlan> findCurrentActiveByRecurringTemplateId(
			@Param("templateId") UUID templateId,
			@Param("today") LocalDate today);

	@Query("""
		SELECT b FROM BudgetPlan b
		WHERE b.expenseTracker.id = :trackerId
		AND b.category IS NOT NULL
		AND b.active = true
		AND b.validFrom <= :today
		AND (b.validTo IS NULL OR b.validTo >= :today)
		""")
	List<BudgetPlan> findAllCurrentActiveByExpenseTrackerIdWithCategory(
			@Param("trackerId") UUID trackerId,
			@Param("today") LocalDate today);

	@Query("""
		SELECT b FROM BudgetPlan b
		WHERE b.expenseTracker.id = :trackerId
		AND b.category IS NOT NULL
		AND b.active = true
		AND b.validFrom <= :rangeTo
		AND (b.validTo IS NULL OR b.validTo >= :rangeFrom)
		""")
	List<BudgetPlan> findAllActiveByExpenseTrackerIdWithCategoryInRange(
			@Param("trackerId") UUID trackerId,
			@Param("rangeFrom") LocalDate rangeFrom,
			@Param("rangeTo") LocalDate rangeTo);
}