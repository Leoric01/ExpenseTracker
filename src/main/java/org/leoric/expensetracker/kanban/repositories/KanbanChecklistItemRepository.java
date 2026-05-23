package org.leoric.expensetracker.kanban.repositories;

import org.leoric.expensetracker.kanban.models.KanbanChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KanbanChecklistItemRepository extends JpaRepository<KanbanChecklistItem, UUID> {

	List<KanbanChecklistItem> findByCardIdOrderByItemOrderAscTitleAsc(UUID cardId);

	List<KanbanChecklistItem> findByCardIdAndIdIn(UUID cardId, Collection<UUID> ids);

	Optional<KanbanChecklistItem> findByCardIdAndId(UUID cardId, UUID id);
}