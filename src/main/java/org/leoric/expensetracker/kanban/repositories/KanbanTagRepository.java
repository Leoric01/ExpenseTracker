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

	Optional<KanbanTag> findByExpenseTrackerIdAndNameIgnoreCaseAndIsDeletedFalse(UUID expenseTrackerId, String name);

	List<KanbanTag> findByExpenseTrackerIdAndIdInAndIsDeletedFalse(UUID expenseTrackerId, Collection<UUID> ids);

	List<KanbanTag> findByExpenseTrackerIdAndIsDeletedFalseOrderByTagOrderAscNameAsc(UUID expenseTrackerId);
}