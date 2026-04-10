package org.leoric.expensetracker.habit.repositories;

import org.leoric.expensetracker.habit.models.HabitScheduleSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface HabitScheduleSlotRepository extends JpaRepository<HabitScheduleSlot, UUID> {

	List<HabitScheduleSlot> findByHabitIdOrderByDayOfWeekAscDayBlockAscSortOrderAsc(UUID habitId);

	void deleteByHabitId(UUID habitId);

	@Query("""
			SELECT s FROM HabitScheduleSlot s
			WHERE s.habit.id IN :habitIds
			ORDER BY s.dayOfWeek ASC, s.dayBlock ASC, s.sortOrder ASC
			""")
	List<HabitScheduleSlot> findAllByHabitIds(@Param("habitIds") Collection<UUID> habitIds);

	@Query("""
			SELECT s FROM HabitScheduleSlot s
			WHERE s.habit.expenseTracker.id = :trackerId
			AND s.habit.isDeleted = false
			AND s.habit.active = true
			AND s.habit.validFrom <= :date
			AND (s.habit.validTo IS NULL OR s.habit.validTo >= :date)
			AND s.dayOfWeek = :dayOfWeek
			ORDER BY s.dayBlock ASC, s.sortOrder ASC, s.habit.sortOrder ASC, s.habit.name ASC
			""")
	List<HabitScheduleSlot> findAgendaSlotsForDate(
			@Param("trackerId") UUID trackerId,
			@Param("date") LocalDate date,
			@Param("dayOfWeek") DayOfWeek dayOfWeek);

	@Query("""
			SELECT s FROM HabitScheduleSlot s
			WHERE s.habit.expenseTracker.id = :trackerId
			AND s.habit.isDeleted = false
			AND s.habit.active = true
			AND s.habit.validFrom <= :endDate
			AND (s.habit.validTo IS NULL OR s.habit.validTo >= :startDate)
			ORDER BY s.habit.id ASC, s.dayOfWeek ASC, s.dayBlock ASC, s.sortOrder ASC
			""")
	List<HabitScheduleSlot> findAllActiveByExpenseTrackerIdAndDateRange(
			@Param("trackerId") UUID trackerId,
			@Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate);

	@Query("""
		SELECT
			h.id AS habitId,
			h.name AS habitName,
			h.description AS habitDescription,
			h.habitType AS habitType,
			h.expectedMinutes AS expectedMinutes,
			h.sortOrder AS habitSortOrder,
			h.satisfactionScore AS satisfactionScore,
			h.utilityScore AS utilityScore,
			h.estimatedPrice AS estimatedPrice,
			s.dayBlock AS dayBlock,
			s.sortOrder AS slotSortOrder,
			c.id AS completionId,
			c.date AS completionDate,
			c.status AS completionStatus,
			c.note AS completionNote,
			c.completedAt AS completedAt,
			c.satisfactionScore AS completionSatisfactionScore,
			c.executionScore AS completionExecutionScore,
			c.actualPrice AS completionActualPrice
		FROM HabitScheduleSlot s
		JOIN s.habit h
		LEFT JOIN HabitCompletion c
			ON c.habit.id = h.id
			AND c.date = :date
		WHERE h.expenseTracker.id = :trackerId
		AND h.isDeleted = false
		AND h.active = true
		AND h.validFrom <= :date
		AND (h.validTo IS NULL OR h.validTo >= :date)
		AND s.dayOfWeek = :dayOfWeek
		ORDER BY s.dayBlock ASC, s.sortOrder ASC, h.sortOrder ASC, h.name ASC
		""")
	List<HabitAgendaProjection> findDailyAgendaProjection(
			@Param("trackerId") UUID trackerId,
			@Param("date") LocalDate date,
			@Param("dayOfWeek") DayOfWeek dayOfWeek);
}