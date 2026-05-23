package org.leoric.expensetracker.kanban.repositories;

import org.leoric.expensetracker.kanban.models.KanbanBoard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface KanbanBoardRepository extends JpaRepository<KanbanBoard, UUID> {

	Optional<KanbanBoard> findByIdAndIsDeletedFalse(UUID id);

	boolean existsByExpenseTrackerIdAndNameIgnoreCaseAndIsDeletedFalse(UUID expenseTrackerId, String name);

	boolean existsByExpenseTrackerIdAndNameIgnoreCaseAndIsDeletedFalseAndIdNot(UUID expenseTrackerId, String name, UUID id);

	List<KanbanBoard> findByExpenseTrackerIdAndIsDeletedFalseOrderByBoardOrderAscNameAsc(UUID expenseTrackerId);

	List<KanbanBoard> findByExpenseTrackerIdAndIdInAndIsDeletedFalse(UUID expenseTrackerId, Set<UUID> ids);
}