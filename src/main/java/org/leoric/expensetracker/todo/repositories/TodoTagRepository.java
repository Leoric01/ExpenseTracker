package org.leoric.expensetracker.todo.repositories;

import org.leoric.expensetracker.todo.models.TodoTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TodoTagRepository extends JpaRepository<TodoTag, UUID> {

	Optional<TodoTag> findByIdAndIsDeletedFalse(UUID id);

	boolean existsByExpenseTrackerIdAndNameIgnoreCaseAndIsDeletedFalse(UUID expenseTrackerId, String name);

	boolean existsByExpenseTrackerIdAndNameIgnoreCaseAndIsDeletedFalseAndIdNot(UUID expenseTrackerId, String name, UUID id);

	List<TodoTag> findByExpenseTrackerIdAndIsDeletedFalseOrderByNameAsc(UUID expenseTrackerId);

	List<TodoTag> findByExpenseTrackerIdAndIdInAndIsDeletedFalse(UUID expenseTrackerId, Collection<UUID> ids);
}