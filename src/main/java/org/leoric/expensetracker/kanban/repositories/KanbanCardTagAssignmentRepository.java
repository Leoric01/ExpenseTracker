package org.leoric.expensetracker.kanban.repositories;

import org.leoric.expensetracker.kanban.models.KanbanCardTagAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface KanbanCardTagAssignmentRepository extends JpaRepository<KanbanCardTagAssignment, UUID> {

	@Query("""
			SELECT a FROM KanbanCardTagAssignment a
			JOIN FETCH a.tag t
			WHERE a.card.id IN :cardIds
			AND t.isDeleted = false
			ORDER BY t.tagOrder ASC, t.name ASC
			""")
	List<KanbanCardTagAssignment> findAllByCardIdsWithTag(@Param("cardIds") Collection<UUID> cardIds);

	@Modifying(flushAutomatically = true)
	@Query("DELETE FROM KanbanCardTagAssignment a WHERE a.card.id = :cardId")
	void deleteByCardId(@Param("cardId") UUID cardId);

	@Modifying(flushAutomatically = true)
	@Query("DELETE FROM KanbanCardTagAssignment a WHERE a.tag.id = :tagId")
	void deleteByTagId(@Param("tagId") UUID tagId);
}