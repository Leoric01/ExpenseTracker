package org.leoric.expensetracker.kanban.repositories;

import org.leoric.expensetracker.kanban.models.KanbanStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KanbanStageRepository extends JpaRepository<KanbanStage, UUID> {

	Optional<KanbanStage> findByIdAndIsDeletedFalse(UUID id);

	boolean existsByBoardIdAndNameIgnoreCaseAndIsDeletedFalse(UUID boardId, String name);

	boolean existsByBoardIdAndNameIgnoreCaseAndIsDeletedFalseAndIdNot(UUID boardId, String name, UUID id);

	List<KanbanStage> findByBoardIdAndIsDeletedFalseOrderByStageOrderAscNameAsc(UUID boardId);

	List<KanbanStage> findByBoardIdAndIdInAndIsDeletedFalse(UUID boardId, Collection<UUID> ids);
}