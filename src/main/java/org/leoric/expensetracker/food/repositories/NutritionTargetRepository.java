package org.leoric.expensetracker.food.repositories;

import org.leoric.expensetracker.food.models.NutritionTarget;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NutritionTargetRepository extends JpaRepository<NutritionTarget, UUID> {

	Page<NutritionTarget> findByExpenseTrackerId(UUID expenseTrackerId, Pageable pageable);

	Optional<NutritionTarget> findTopByGoalPlanIdOrderByEffectiveFromDesc(UUID goalPlanId);

	Optional<NutritionTarget> findTopByExpenseTrackerIdOrderByEffectiveFromDesc(UUID expenseTrackerId);

	void deleteByExpenseTrackerId(UUID expenseTrackerId);
}