package org.leoric.expensetracker.todo.repositories;

import org.leoric.expensetracker.todo.models.TodoTagAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface TodoTagAssignmentRepository extends JpaRepository<TodoTagAssignment, UUID> {

	@Query("""
			SELECT a FROM TodoTagAssignment a
			JOIN FETCH a.tag t
			WHERE a.todo.id IN :todoIds
			AND a.todo.isDeleted = false
			AND t.isDeleted = false
			ORDER BY t.name ASC
			""")
	List<TodoTagAssignment> findAllByTodoIdsWithTag(@Param("todoIds") Collection<UUID> todoIds);

	void deleteByTodoId(UUID todoId);

	void deleteByTodoIdIn(Collection<UUID> todoIds);

	void deleteByTagId(UUID tagId);
}