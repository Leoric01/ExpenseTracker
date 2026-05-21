package org.leoric.expensetracker.kanban.repositories;

import org.leoric.expensetracker.kanban.models.KanbanCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KanbanCardRepository extends JpaRepository<KanbanCard, UUID> {

	Optional<KanbanCard> findByIdAndIsDeletedFalse(UUID id);

	boolean existsByBoardIdAndParentIsNullAndTitleIgnoreCaseAndIsDeletedFalse(UUID boardId, String title);

	boolean existsByBoardIdAndParentIsNullAndTitleIgnoreCaseAndIsDeletedFalseAndIdNot(UUID boardId, String title, UUID id);

	boolean existsByBoardIdAndParentIdAndTitleIgnoreCaseAndIsDeletedFalse(UUID boardId, UUID parentId, String title);

	boolean existsByBoardIdAndParentIdAndTitleIgnoreCaseAndIsDeletedFalseAndIdNot(UUID boardId, UUID parentId, String title, UUID id);

	List<KanbanCard> findByBoardIdAndIsDeletedFalse(UUID boardId);

	boolean existsByBoardIdAndStageIdAndIsDeletedFalse(UUID boardId, UUID stageId);

	List<KanbanCard> findByBoardIdAndStageIdAndIsDeletedFalse(UUID boardId, UUID stageId);

	List<KanbanCard> findByBoardIdAndParentIdInAndIsDeletedFalse(UUID boardId, Collection<UUID> parentIds);
}