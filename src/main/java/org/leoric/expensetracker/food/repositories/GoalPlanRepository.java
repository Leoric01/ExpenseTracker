package org.leoric.expensetracker.food.repositories;

import org.leoric.expensetracker.food.models.GoalPlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GoalPlanRepository extends JpaRepository<GoalPlan, UUID> {

	Page<GoalPlan> findByExpenseTrackerId(UUID expenseTrackerId, Pageable pageable);

	Optional<GoalPlan> findByExpenseTrackerIdAndActiveTrue(UUID expenseTrackerId);

	Optional<GoalPlan> findByIdAndExpenseTrackerId(UUID goalPlanId, UUID expenseTrackerId);

	boolean existsByExpenseTrackerIdAndNameIgnoreCase(UUID expenseTrackerId, String name);

	@Query("""
			SELECT gp FROM GoalPlan gp
			WHERE gp.expenseTracker.id = :trackerId
			AND (
				LOWER(gp.name) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(CAST(gp.goalType AS string)) LIKE LOWER(CONCAT('%', :search, '%'))
			)
			""")
	Page<GoalPlan> findByExpenseTrackerIdWithSearch(
			@Param("trackerId") UUID trackerId,
			@Param("search") String search,
			Pageable pageable);

	void deleteByExpenseTrackerId(UUID expenseTrackerId);
}