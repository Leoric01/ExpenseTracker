package org.leoric.expensetracker.habit.repositories;

import org.leoric.expensetracker.habit.models.Habit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface HabitRepository extends JpaRepository<Habit, UUID> {

	void deleteByExpenseTrackerId(UUID expenseTrackerId);

	boolean existsByExpenseTrackerIdAndNameIgnoreCaseAndIsDeletedFalse(UUID expenseTrackerId, String name);

	boolean existsByExpenseTrackerIdAndNameIgnoreCaseAndIsDeletedFalseAndIdNot(UUID expenseTrackerId, String name, UUID id);

	@Query("""
			SELECT h FROM Habit h
			WHERE h.expenseTracker.id = :trackerId
			AND h.isDeleted = false
			AND (:active IS NULL OR h.active = :active)
			AND (:search IS NULL OR LOWER(h.name) LIKE LOWER(CONCAT('%', :search, '%')))
			ORDER BY h.sortOrder ASC, h.name ASC
			""")
	Page<Habit> findAllByExpenseTrackerIdWithFilters(
			@Param("trackerId") UUID trackerId,
			@Param("search") String search,
			@Param("active") Boolean active,
			Pageable pageable);
}