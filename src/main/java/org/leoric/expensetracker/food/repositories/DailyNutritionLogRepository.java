package org.leoric.expensetracker.food.repositories;

import org.leoric.expensetracker.food.models.DailyNutritionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyNutritionLogRepository extends JpaRepository<DailyNutritionLog, UUID> {

	Page<DailyNutritionLog> findByExpenseTrackerId(UUID expenseTrackerId, Pageable pageable);

	Optional<DailyNutritionLog> findByExpenseTrackerIdAndLogDate(UUID expenseTrackerId, LocalDate logDate);

	boolean existsByExpenseTrackerIdAndLogDate(UUID expenseTrackerId, LocalDate logDate);

	@Query("""
			SELECT d FROM DailyNutritionLog d
			WHERE d.expenseTracker.id = :trackerId
			AND d.logDate BETWEEN :from AND :to
			ORDER BY d.logDate ASC
			""")
	List<DailyNutritionLog> findByExpenseTrackerIdAndLogDateBetweenOrderByLogDateAsc(
			@Param("trackerId") UUID trackerId,
			@Param("from") LocalDate from,
			@Param("to") LocalDate to);

	@Query("""
			SELECT d FROM DailyNutritionLog d
			WHERE d.goalPlan.id = :goalPlanId
			AND d.logDate BETWEEN :from AND :to
			ORDER BY d.logDate ASC
			""")
	List<DailyNutritionLog> findByGoalPlanIdAndLogDateBetweenOrderByLogDateAsc(
			@Param("goalPlanId") UUID goalPlanId,
			@Param("from") LocalDate from,
			@Param("to") LocalDate to);

	void deleteByExpenseTrackerId(UUID expenseTrackerId);
}