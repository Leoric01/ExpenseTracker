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

	Optional<KanbanCard> findById(UUID id);

	boolean existsByStageBoardIdAndTitleIgnoreCase(UUID boardId, String title);

	boolean existsByStageBoardIdAndTitleIgnoreCaseAndIdNot(UUID boardId, String title, UUID id);

	List<KanbanCard> findByStageBoardIdOrderByCardOrderAscTitleAsc(UUID boardId);

	boolean existsByStageBoardIdAndStageId(UUID boardId, UUID stageId);

	List<KanbanCard> findByStageBoardIdAndStageId(UUID boardId, UUID stageId);

	List<KanbanCard> findByStageIdOrderByCardOrderAscTitleAsc(UUID stageId);

	List<KanbanCard> findByStageIdAndIdIn(UUID stageId, Collection<UUID> ids);
}