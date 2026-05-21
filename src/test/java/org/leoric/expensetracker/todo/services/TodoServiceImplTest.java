package org.leoric.expensetracker.todo.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.handler.exceptions.InvalidTodoException;
import org.leoric.expensetracker.handler.exceptions.TodoTagNotFoundException;
import org.leoric.expensetracker.todo.dtos.TodoArchiveUpdateRequestDto;
import org.leoric.expensetracker.todo.dtos.TodoStatusUpdateRequestDto;
import org.leoric.expensetracker.todo.dtos.TodoUpsertRequestDto;
import org.leoric.expensetracker.todo.mapstruct.TodoMapper;
import org.leoric.expensetracker.todo.models.Todo;
import org.leoric.expensetracker.todo.models.TodoTag;
import org.leoric.expensetracker.todo.models.TodoTagAssignment;
import org.leoric.expensetracker.todo.models.constants.TodoPriority;
import org.leoric.expensetracker.todo.models.constants.TodoStatus;
import org.leoric.expensetracker.todo.repositories.TodoRepository;
import org.leoric.expensetracker.todo.repositories.TodoTagAssignmentRepository;
import org.leoric.expensetracker.todo.repositories.TodoTagRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TodoServiceImplTest {

	@Mock
	private TodoRepository todoRepository;
	@Mock
	private TodoTagRepository todoTagRepository;
	@Mock
	private TodoTagAssignmentRepository todoTagAssignmentRepository;
	@Mock
	private ExpenseTrackerRepository expenseTrackerRepository;
	@Mock
	private TodoMapper todoMapper;

	@InjectMocks
	private TodoServiceImpl todoService;

	private UUID trackerId;
	private User user;
	private ExpenseTracker tracker;

	@BeforeEach
	void setUp() {
		trackerId = UUID.randomUUID();
		user = User.builder().id(UUID.randomUUID()).email("todo.tester@example.com").build();
		tracker = ExpenseTracker.builder().id(trackerId).name("Main tracker").build();
	}

	@Test
	void todoUpdateStatus_shouldPropagateDoneStatusToWholeSubtree() {
		Todo root = todo(UUID.randomUUID(), tracker, null, "Root");
		Todo child = todo(UUID.randomUUID(), tracker, root, "Child");
		Todo grandchild = todo(UUID.randomUUID(), tracker, child, "Grandchild");
		Set<UUID> expectedIds = Set.of(root.getId(), child.getId(), grandchild.getId());
		stubSubtree(root, Map.of(
				root.getId(), List.of(child),
				child.getId(), List.of(grandchild)
		));

		todoService.todoUpdateStatus(user, trackerId, root.getId(), new TodoStatusUpdateRequestDto(TodoStatus.DONE));
		verify(todoRepository).saveAll(org.mockito.ArgumentMatchers.argThat(persisted ->
				toTodoList(persisted).size() == 3
						&& toTodoList(persisted).stream().map(Todo::getId).collect(Collectors.toSet()).equals(expectedIds)
						&& toTodoList(persisted).stream().allMatch(t -> t.getStatus() == TodoStatus.DONE)
						&& toTodoList(persisted).stream().allMatch(t -> t.getCompletedAt() != null)
		));
	}

	@Test
	void todoUpdateStatus_shouldClearCompletedAtWhenStatusIsNotDone() {
		Todo root = todo(UUID.randomUUID(), tracker, null, "Root");
		root.setCompletedAt(Instant.parse("2026-01-01T00:00:00Z"));
		Todo child = todo(UUID.randomUUID(), tracker, root, "Child");
		child.setCompletedAt(Instant.parse("2026-01-02T00:00:00Z"));
		Set<UUID> expectedIds = Set.of(root.getId(), child.getId());
		stubSubtree(root, Map.of(root.getId(), List.of(child)));

		todoService.todoUpdateStatus(user, trackerId, root.getId(), new TodoStatusUpdateRequestDto(TodoStatus.TODO));

		verify(todoRepository).saveAll(org.mockito.ArgumentMatchers.argThat(persisted ->
				toTodoList(persisted).size() == 2
						&& toTodoList(persisted).stream().map(Todo::getId).collect(Collectors.toSet()).equals(expectedIds)
						&& toTodoList(persisted).stream().allMatch(t -> t.getStatus() == TodoStatus.TODO)
						&& toTodoList(persisted).stream().allMatch(t -> t.getCompletedAt() == null)
		));
	}

	@Test
	void todoArchive_shouldPropagateArchiveFlagToWholeSubtree() {
		Todo root = todo(UUID.randomUUID(), tracker, null, "Root");
		Todo child = todo(UUID.randomUUID(), tracker, root, "Child");
		Set<UUID> expectedIds = Set.of(root.getId(), child.getId());
		stubSubtree(root, Map.of(root.getId(), List.of(child)));

		todoService.todoArchive(user, trackerId, root.getId(), new TodoArchiveUpdateRequestDto(true));
		verify(todoRepository).saveAll(org.mockito.ArgumentMatchers.argThat(persisted ->
				toTodoList(persisted).size() == 2
						&& toTodoList(persisted).stream().map(Todo::getId).collect(Collectors.toSet()).equals(expectedIds)
						&& toTodoList(persisted).stream().allMatch(t -> !t.isActive())
						&& toTodoList(persisted).stream().allMatch(t -> t.getArchivedAt() != null)
		));
	}

	@Test
	void todoDelete_shouldSoftDeleteSubtreeAndRemoveTagAssignments() {
		Todo root = todo(UUID.randomUUID(), tracker, null, "Root");
		root.setArchivedAt(Instant.parse("2026-01-01T00:00:00Z"));
		Todo child = todo(UUID.randomUUID(), tracker, root, "Child");
		Set<UUID> expectedIds = Set.of(root.getId(), child.getId());
		stubSubtree(root, Map.of(root.getId(), List.of(child)));

		todoService.todoDelete(user, trackerId, root.getId());
		verify(todoRepository).saveAll(org.mockito.ArgumentMatchers.argThat(persisted ->
				toTodoList(persisted).size() == 2
						&& toTodoList(persisted).stream().map(Todo::getId).collect(Collectors.toSet()).equals(expectedIds)
						&& toTodoList(persisted).stream().allMatch(Todo::isDeleted)
						&& toTodoList(persisted).stream().allMatch(t -> !t.isActive())
		));
		assertThat(root.getArchivedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
		assertThat(child.getArchivedAt()).isNotNull();
		verify(todoTagAssignmentRepository).deleteByTodoIdIn(org.mockito.ArgumentMatchers.argThat(ids -> new HashSet<>(ids).equals(expectedIds)));
	}

	@Test
	void todoUpdate_shouldRejectMoveWhenDepthLimitWouldBeExceeded() {
		Todo current = todo(UUID.randomUUID(), tracker, null, "Current");
		Todo currentChild = todo(UUID.randomUUID(), tracker, current, "Current child");

		Todo level1 = todo(UUID.randomUUID(), tracker, null, "L1");
		Todo level2 = todo(UUID.randomUUID(), tracker, level1, "L2");
		Todo level3 = todo(UUID.randomUUID(), tracker, level2, "L3");
		Todo level4 = todo(UUID.randomUUID(), tracker, level3, "L4");
		Todo level5 = todo(UUID.randomUUID(), tracker, level4, "L5");
		Todo level6 = todo(UUID.randomUUID(), tracker, level5, "L6");
		Todo level7 = todo(UUID.randomUUID(), tracker, level6, "L7");
		Todo level8 = todo(UUID.randomUUID(), tracker, level7, "L8");
		Todo level9 = todo(UUID.randomUUID(), tracker, level8, "L9");
		Todo level10 = todo(UUID.randomUUID(), tracker, level9, "L10");
		Todo level11 = todo(UUID.randomUUID(), tracker, level10, "L11");
		Todo level12 = todo(UUID.randomUUID(), tracker, level11, "L12");
		Todo level13 = todo(UUID.randomUUID(), tracker, level12, "L13");
		Todo targetParent = todo(UUID.randomUUID(), tracker, level13, "L14");

		when(todoRepository.findByIdAndIsDeletedFalse(current.getId())).thenReturn(Optional.of(current));
		when(todoRepository.findByIdAndIsDeletedFalse(targetParent.getId())).thenReturn(Optional.of(targetParent));
		when(todoRepository.findByExpenseTrackerIdAndParentIdInAndIsDeletedFalse(eq(trackerId), anyCollection()))
				.thenAnswer(invocation -> {
					@SuppressWarnings("unchecked")
					var parentIds = (java.util.Collection<UUID>) invocation.getArgument(1);
					if (parentIds.contains(current.getId())) {
						return List.of(currentChild);
					}
					return List.of();
				});

		TodoUpsertRequestDto request = new TodoUpsertRequestDto(
				targetParent.getId(),
				"Moved",
				null,
				TodoStatus.TODO,
				TodoPriority.MEDIUM,
				null,
				null,
				null,
				0,
				true,
				null
		);

		assertThatThrownBy(() -> todoService.todoUpdate(user, trackerId, current.getId(), request))
				.isInstanceOf(InvalidTodoException.class)
				.hasMessageContaining("depth limit");
	}

	@Test
	void todoUpdate_shouldRejectSelfParent() {
		Todo current = todo(UUID.randomUUID(), tracker, null, "Current");
		when(todoRepository.findByIdAndIsDeletedFalse(current.getId())).thenReturn(Optional.of(current));

		TodoUpsertRequestDto request = new TodoUpsertRequestDto(
				current.getId(),
				"Moved",
				null,
				TodoStatus.TODO,
				TodoPriority.MEDIUM,
				null,
				null,
				null,
				0,
				true,
				null
		);

		assertThatThrownBy(() -> todoService.todoUpdate(user, trackerId, current.getId(), request))
				.isInstanceOf(InvalidTodoException.class)
				.hasMessageContaining("parent of itself");
	}

	@Test
	void todoUpdate_shouldRejectMoveToOwnDescendant() {
		Todo current = todo(UUID.randomUUID(), tracker, null, "Current");
		Todo child = todo(UUID.randomUUID(), tracker, current, "Child");

		when(todoRepository.findByIdAndIsDeletedFalse(current.getId())).thenReturn(Optional.of(current));
		when(todoRepository.findByIdAndIsDeletedFalse(child.getId())).thenReturn(Optional.of(child));

		TodoUpsertRequestDto request = new TodoUpsertRequestDto(
				child.getId(),
				"Moved",
				null,
				TodoStatus.TODO,
				TodoPriority.MEDIUM,
				null,
				null,
				null,
				0,
				true,
				null
		);

		assertThatThrownBy(() -> todoService.todoUpdate(user, trackerId, current.getId(), request))
				.isInstanceOf(InvalidTodoException.class)
				.hasMessageContaining("Circular relationship");
	}

	@Test
	void todoReplaceTags_shouldReplaceAssignmentsWithDistinctTags() {
		Todo todo = todo(UUID.randomUUID(), tracker, null, "Task");
		TodoTag tagA = tag(UUID.randomUUID(), tracker, "urgent");
		TodoTag tagB = tag(UUID.randomUUID(), tracker, "home");

		when(todoRepository.findByIdAndIsDeletedFalse(todo.getId())).thenReturn(Optional.of(todo));
		when(todoTagRepository.findByExpenseTrackerIdAndIdInAndIsDeletedFalse(eq(trackerId), anySet()))
				.thenReturn(List.of(tagA, tagB));

		todoService.todoReplaceTags(user, trackerId, todo.getId(), List.of(tagA.getId(), tagA.getId(), tagB.getId()));

		verify(todoTagAssignmentRepository).deleteByTodoId(todo.getId());
		verify(todoTagAssignmentRepository).saveAll(org.mockito.ArgumentMatchers.argThat(assignments -> {
			Set<UUID> tagIds = toAssignmentList(assignments).stream()
					.map(a -> a.getTag().getId())
					.collect(Collectors.toSet());
			return tagIds.equals(Set.of(tagA.getId(), tagB.getId()));
		}));
	}

	@Test
	void todoReplaceTags_shouldFailWhenSomeTagDoesNotExistInTracker() {
		Todo todo = todo(UUID.randomUUID(), tracker, null, "Task");
		TodoTag tagA = tag(UUID.randomUUID(), tracker, "urgent");
		UUID missingTagId = UUID.randomUUID();

		when(todoRepository.findByIdAndIsDeletedFalse(todo.getId())).thenReturn(Optional.of(todo));
		when(todoTagRepository.findByExpenseTrackerIdAndIdInAndIsDeletedFalse(eq(trackerId), anySet()))
				.thenReturn(List.of(tagA));

		assertThatThrownBy(() -> todoService.todoReplaceTags(user, trackerId, todo.getId(), List.of(tagA.getId(), missingTagId)))
				.isInstanceOf(TodoTagNotFoundException.class);
		verify(todoTagAssignmentRepository, never()).saveAll(any());
	}

	@Test
	void todoReplaceTags_shouldOnlyClearAssignmentsWhenTagListIsEmpty() {
		Todo todo = todo(UUID.randomUUID(), tracker, null, "Task");
		when(todoRepository.findByIdAndIsDeletedFalse(todo.getId())).thenReturn(Optional.of(todo));

		todoService.todoReplaceTags(user, trackerId, todo.getId(), List.of());

		verify(todoTagAssignmentRepository).deleteByTodoId(todo.getId());
		verify(todoTagAssignmentRepository, never()).saveAll(any());
	}

	@Test
	void todoFindTree_shouldRejectInvalidDateRange() {
		assertThatThrownBy(() -> todoService.todoFindTree(
				user,
				trackerId,
				null,
				null,
				null,
				null,
				LocalDate.of(2026, 5, 20),
				LocalDate.of(2026, 5, 1)
		)).isInstanceOf(InvalidTodoException.class)
				.hasMessageContaining("dueFrom");
	}

	private void stubSubtree(Todo root, Map<UUID, List<Todo>> childrenByParentId) {
		when(todoRepository.findByIdAndIsDeletedFalse(root.getId())).thenReturn(Optional.of(root));
		when(todoRepository.findByExpenseTrackerIdAndParentIdInAndIsDeletedFalse(eq(trackerId), anyCollection()))
				.thenAnswer(invocation -> {
					@SuppressWarnings("unchecked")
					var parentIds = (java.util.Collection<UUID>) invocation.getArgument(1);
					return parentIds.stream()
							.flatMap(id -> childrenByParentId.getOrDefault(id, List.of()).stream())
							.toList();
				});
	}

	private Todo todo(UUID id, ExpenseTracker expenseTracker, Todo parent, String title) {
		return Todo.builder()
				.id(id)
				.expenseTracker(expenseTracker)
				.parent(parent)
				.title(title)
				.status(TodoStatus.TODO)
				.priority(TodoPriority.MEDIUM)
				.sortOrder(0)
				.active(true)
				.isDeleted(false)
				.createdDate(Instant.now())
				.lastModifiedDate(Instant.now())
				.build();
	}

	private TodoTag tag(UUID id, ExpenseTracker expenseTracker, String name) {
		return TodoTag.builder()
				.id(id)
				.expenseTracker(expenseTracker)
				.name(name)
				.active(true)
				.isDeleted(false)
				.build();
	}

	private List<Todo> toTodoList(Iterable<Todo> persisted) {
		List<Todo> list = new java.util.ArrayList<>();
		persisted.forEach(list::add);
		return list;
	}

	private List<TodoTagAssignment> toAssignmentList(Iterable<TodoTagAssignment> assignments) {
		List<TodoTagAssignment> list = new java.util.ArrayList<>();
		assignments.forEach(list::add);
		return list;
	}
}