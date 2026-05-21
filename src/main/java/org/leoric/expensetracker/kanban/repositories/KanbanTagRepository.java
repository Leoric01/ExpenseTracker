package org.leoric.expensetracker.kanban.repositories;

import org.leoric.expensetracker.kanban.models.KanbanTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KanbanTagRepository extends JpaRepository<KanbanTag, UUID> {

	Optional<KanbanTag> findByIdAndIsDeletedFalse(UUID id);

	Optional<KanbanTag> findByBoardIdAndNameIgnoreCaseAndIsDeletedFalse(UUID boardId, String name);

	List<KanbanTag> findByBoardIdAndIdInAndIsDeletedFalse(UUID boardId, Collection<UUID> ids);

	List<KanbanTag> findByBoardIdAndIsDeletedFalseOrderByNameAsc(UUID boardId);
}