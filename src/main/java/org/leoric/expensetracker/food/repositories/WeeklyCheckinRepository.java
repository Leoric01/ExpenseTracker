package org.leoric.expensetracker.food.repositories;

import org.leoric.expensetracker.food.models.WeeklyCheckin;
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
public interface WeeklyCheckinRepository extends JpaRepository<WeeklyCheckin, UUID> {

	Page<WeeklyCheckin> findByExpenseTrackerId(UUID expenseTrackerId, Pageable pageable);

	Page<WeeklyCheckin> findByGoalPlanIdAndExpenseTrackerId(UUID goalPlanId, UUID expenseTrackerId, Pageable pageable);

	List<WeeklyCheckin> findByGoalPlanIdOrderByWeekIndexAsc(UUID goalPlanId);

	Optional<WeeklyCheckin> findByGoalPlanIdAndWeekIndex(UUID goalPlanId, Integer weekIndex);

	Optional<WeeklyCheckin> findTopByGoalPlanIdOrderByWeekIndexDesc(UUID goalPlanId);

	@Query("""
			SELECT wc FROM WeeklyCheckin wc
			WHERE wc.goalPlan.id = :goalPlanId
			AND wc.weekStartDate >= :from
			AND wc.weekEndDate <= :to
			ORDER BY wc.weekIndex ASC
			""")
	List<WeeklyCheckin> findByGoalPlanIdAndWeekRange(
			@Param("goalPlanId") UUID goalPlanId,
			@Param("from") LocalDate from,
			@Param("to") LocalDate to);

	void deleteByExpenseTrackerId(UUID expenseTrackerId);
}