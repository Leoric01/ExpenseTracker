package org.leoric.expensetracker.habit.repositories;

import org.leoric.expensetracker.habit.models.HabitCompletion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HabitCompletionRepository extends JpaRepository<HabitCompletion, UUID> {

	Optional<HabitCompletion> findByHabitIdAndDate(UUID habitId, LocalDate date);

	@Query("""
			SELECT c FROM HabitCompletion c
			WHERE c.habit.expenseTracker.id = :trackerId
			AND c.date = :date
			ORDER BY c.habit.sortOrder ASC, c.habit.name ASC
			""")
	List<HabitCompletion> findAllByExpenseTrackerIdAndDate(
			@Param("trackerId") UUID trackerId,
			@Param("date") LocalDate date);

	@Query("""
			SELECT c FROM HabitCompletion c
			WHERE c.habit.expenseTracker.id = :trackerId
			AND c.date BETWEEN :startDate AND :endDate
			ORDER BY c.date ASC, c.habit.sortOrder ASC, c.habit.name ASC
			""")
	List<HabitCompletion> findAllByExpenseTrackerIdAndDateRange(
			@Param("trackerId") UUID trackerId,
			@Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate);

	@Query("""
			SELECT c FROM HabitCompletion c
			WHERE c.habit.id IN :habitIds
			AND c.date BETWEEN :startDate AND :endDate
			ORDER BY c.date ASC
			""")
	List<HabitCompletion> findAllByHabitIdsAndDateRange(
			@Param("habitIds") Collection<UUID> habitIds,
			@Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate);

	boolean existsByHabitIdAndDate(UUID habitId, LocalDate date);
}