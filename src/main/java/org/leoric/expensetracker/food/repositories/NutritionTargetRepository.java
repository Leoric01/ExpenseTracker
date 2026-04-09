package org.leoric.expensetracker.food.repositories;

import org.leoric.expensetracker.food.models.NutritionTarget;
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
public interface NutritionTargetRepository extends JpaRepository<NutritionTarget, UUID> {

	Page<NutritionTarget> findByExpenseTrackerId(UUID expenseTrackerId, Pageable pageable);

	List<NutritionTarget> findByGoalPlanIdOrderByEffectiveFromDesc(UUID goalPlanId);

	Optional<NutritionTarget> findTopByGoalPlanIdOrderByEffectiveFromDesc(UUID goalPlanId);

	Optional<NutritionTarget> findTopByExpenseTrackerIdOrderByEffectiveFromDesc(UUID expenseTrackerId);

	@Query("""
			SELECT nt FROM NutritionTarget nt
			WHERE nt.goalPlan.id = :goalPlanId
			AND :date >= nt.effectiveFrom
			AND (nt.effectiveTo IS NULL OR :date <= nt.effectiveTo)
			""")
	Optional<NutritionTarget> findEffectiveTargetByGoalPlanIdAndDate(
			@Param("goalPlanId") UUID goalPlanId,
			@Param("date") LocalDate date);

	void deleteByExpenseTrackerId(UUID expenseTrackerId);
}