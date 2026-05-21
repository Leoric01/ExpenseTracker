package org.leoric.expensetracker.todo.repositories;

import org.leoric.expensetracker.todo.models.Todo;
import org.leoric.expensetracker.todo.models.constants.TodoPriority;
import org.leoric.expensetracker.todo.models.constants.TodoStatus;
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
public interface TodoRepository extends JpaRepository<Todo, UUID> {

	Optional<Todo> findByIdAndIsDeletedFalse(UUID id);

	boolean existsByExpenseTrackerIdAndParentIsNullAndTitleIgnoreCaseAndIsDeletedFalse(UUID expenseTrackerId, String title);

	boolean existsByExpenseTrackerIdAndParentIsNullAndTitleIgnoreCaseAndIsDeletedFalseAndIdNot(UUID expenseTrackerId, String title, UUID id);

	boolean existsByExpenseTrackerIdAndParentIdAndTitleIgnoreCaseAndIsDeletedFalse(UUID expenseTrackerId, UUID parentId, String title);

	boolean existsByExpenseTrackerIdAndParentIdAndTitleIgnoreCaseAndIsDeletedFalseAndIdNot(UUID expenseTrackerId, UUID parentId, String title, UUID id);

	@Query("""
			SELECT t FROM Todo t
			WHERE t.expenseTracker.id = :trackerId
			AND t.isDeleted = false
			AND (:active IS NULL OR t.active = :active)
			AND (:status IS NULL OR t.status = :status)
			AND (:priority IS NULL OR t.priority = :priority)
			AND (:dueFrom IS NULL OR t.dueDate >= :dueFrom)
			AND (:dueTo IS NULL OR t.dueDate <= :dueTo)
			AND (
				:search IS NULL
				OR LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%'))
				OR LOWER(t.note) LIKE LOWER(CONCAT('%', :search, '%'))
			)
			ORDER BY t.sortOrder ASC, t.title ASC
			""")
	List<Todo> findAllByExpenseTrackerIdWithFilters(
			@Param("trackerId") UUID trackerId,
			@Param("search") String search,
			@Param("active") Boolean active,
			@Param("status") TodoStatus status,
			@Param("priority") TodoPriority priority,
			@Param("dueFrom") LocalDate dueFrom,
			@Param("dueTo") LocalDate dueTo
	);

	List<Todo> findByExpenseTrackerIdAndParentIdInAndIsDeletedFalse(UUID expenseTrackerId, Collection<UUID> parentIds);
}