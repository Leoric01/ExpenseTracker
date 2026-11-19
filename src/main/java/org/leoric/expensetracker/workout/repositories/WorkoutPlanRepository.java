package org.leoric.expensetracker.workout.repositories;

import org.leoric.expensetracker.workout.models.WorkoutPlan;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WorkoutPlanRepository extends JpaRepository<WorkoutPlan, UUID> {
	Optional<WorkoutPlan> findByExpenseTrackerId(UUID trackerId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from WorkoutPlan p where p.expenseTracker.id = :trackerId")
	Optional<WorkoutPlan> findLockedByTrackerId(@Param("trackerId") UUID trackerId);
}
