package org.leoric.expensetracker.todo.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.handler.exceptions.DuplicateTodoTagNameException;
import org.leoric.expensetracker.handler.exceptions.InvalidTodoException;
import org.leoric.expensetracker.handler.exceptions.TodoTagNotFoundException;
import org.leoric.expensetracker.todo.dtos.TodoTagResponseDto;
import org.leoric.expensetracker.todo.dtos.TodoTagUpsertRequestDto;
import org.leoric.expensetracker.todo.mapstruct.TodoMapper;
import org.leoric.expensetracker.todo.models.TodoTag;
import org.leoric.expensetracker.todo.repositories.TodoTagAssignmentRepository;
import org.leoric.expensetracker.todo.repositories.TodoTagRepository;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class TodoTagServiceImplTest {

	@Mock
	private TodoTagRepository todoTagRepository;
	@Mock
	private TodoTagAssignmentRepository todoTagAssignmentRepository;
	@Mock
	private ExpenseTrackerRepository expenseTrackerRepository;
	@Mock
	private TodoMapper todoMapper;

	@InjectMocks
	private TodoTagServiceImpl todoTagService;

	private UUID trackerId;
	private User user;
	private ExpenseTracker tracker;

	@BeforeEach
	void setUp() {
		trackerId = UUID.randomUUID();
		user = User.builder().id(UUID.randomUUID()).email("tag.tester@example.com").build();
		tracker = ExpenseTracker.builder().id(trackerId).name("Main tracker").build();
	}

	@Test
	void todoTagCreate_shouldRejectDuplicateName() {
		when(expenseTrackerRepository.findById(trackerId)).thenReturn(Optional.of(tracker));
		when(todoTagRepository.existsByExpenseTrackerIdAndNameIgnoreCaseAndIsDeletedFalse(trackerId, "urgent"))
				.thenReturn(true);

		assertThatThrownBy(() -> todoTagService.todoTagCreate(
				user,
				trackerId,
				new TodoTagUpsertRequestDto("urgent", "#ffffff", true)
		)).isInstanceOf(DuplicateTodoTagNameException.class);
	}

	@Test
	void todoTagCreate_shouldNormalizeColorAndDefaultActiveToTrue() {
		when(expenseTrackerRepository.findById(trackerId)).thenReturn(Optional.of(tracker));
		when(todoTagRepository.existsByExpenseTrackerIdAndNameIgnoreCaseAndIsDeletedFalse(trackerId, "urgent"))
				.thenReturn(false);
		when(todoTagRepository.save(org.mockito.ArgumentMatchers.any(TodoTag.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(todoMapper.toTagResponseDto(org.mockito.ArgumentMatchers.any(TodoTag.class)))
				.thenAnswer(invocation -> {
					TodoTag tag = invocation.getArgument(0);
					return new TodoTagResponseDto(tag.getId(), tag.getName(), tag.getColor(), tag.isActive(), OffsetDateTime.now(), OffsetDateTime.now());
				});

		TodoTagResponseDto result = todoTagService.todoTagCreate(
				user,
				trackerId,
				new TodoTagUpsertRequestDto(" urgent ", " #ff00aa ", null)
		);

		ArgumentCaptor<TodoTag> saveCaptor = ArgumentCaptor.forClass(TodoTag.class);
		verify(todoTagRepository).save(saveCaptor.capture());
		TodoTag saved = saveCaptor.getValue();
		assertThat(saved.getName()).isEqualTo("urgent");
		assertThat(saved.getColor()).isEqualTo("#FF00AA");
		assertThat(saved.isActive()).isTrue();
		assertThat(result.name()).isEqualTo("urgent");
	}

	@Test
	void todoTagUpdate_shouldRejectBlankName() {
		TodoTag existing = TodoTag.builder().id(UUID.randomUUID()).expenseTracker(tracker).name("old").build();
		when(todoTagRepository.findByIdAndIsDeletedFalse(existing.getId())).thenReturn(Optional.of(existing));

		assertThatThrownBy(() -> todoTagService.todoTagUpdate(
				user,
				trackerId,
				existing.getId(),
				new TodoTagUpsertRequestDto("   ", null, null)
		)).isInstanceOf(InvalidTodoException.class);
	}

	@Test
	void todoTagDelete_shouldSoftDeleteAndRemoveAssignments() {
		TodoTag existing = TodoTag.builder()
				.id(UUID.randomUUID())
				.expenseTracker(tracker)
				.name("house")
				.active(true)
				.isDeleted(false)
				.build();
		when(todoTagRepository.findByIdAndIsDeletedFalse(existing.getId())).thenReturn(Optional.of(existing));
		when(todoTagRepository.save(existing)).thenReturn(existing);

		todoTagService.todoTagDelete(user, trackerId, existing.getId());

		assertThat(existing.isDeleted()).isTrue();
		assertThat(existing.isActive()).isFalse();
		verify(todoTagAssignmentRepository).deleteByTagId(existing.getId());
	}

	@Test
	void todoTagUpdate_shouldRejectDuplicateName() {
		TodoTag existing = TodoTag.builder().id(UUID.randomUUID()).expenseTracker(tracker).name("old").active(true).build();
		when(todoTagRepository.findByIdAndIsDeletedFalse(existing.getId())).thenReturn(Optional.of(existing));
		when(todoTagRepository.existsByExpenseTrackerIdAndNameIgnoreCaseAndIsDeletedFalseAndIdNot(trackerId, "urgent", existing.getId()))
				.thenReturn(true);

		assertThatThrownBy(() -> todoTagService.todoTagUpdate(
				user,
				trackerId,
				existing.getId(),
				new TodoTagUpsertRequestDto("urgent", "#00FF00", true)
		)).isInstanceOf(DuplicateTodoTagNameException.class);
		verify(todoTagRepository, never()).save(existing);
	}

	@Test
	void todoTagUpdate_shouldKeepActiveWhenNullInRequest() {
		TodoTag existing = TodoTag.builder().id(UUID.randomUUID()).expenseTracker(tracker).name("old").active(false).build();
		when(todoTagRepository.findByIdAndIsDeletedFalse(existing.getId())).thenReturn(Optional.of(existing));
		when(todoTagRepository.existsByExpenseTrackerIdAndNameIgnoreCaseAndIsDeletedFalseAndIdNot(trackerId, "renamed", existing.getId()))
				.thenReturn(false);
		when(todoTagRepository.save(existing)).thenReturn(existing);
		when(todoMapper.toTagResponseDto(existing)).thenReturn(
				new TodoTagResponseDto(existing.getId(), existing.getName(), existing.getColor(), existing.isActive(), OffsetDateTime.now(), OffsetDateTime.now())
		);

		todoTagService.todoTagUpdate(
				user,
				trackerId,
				existing.getId(),
				new TodoTagUpsertRequestDto("renamed", "#123456", null)
		);

		assertThat(existing.isActive()).isFalse();
	}

	@Test
	void todoTagDelete_shouldRejectWhenTagBelongsToAnotherTracker() {
		ExpenseTracker anotherTracker = ExpenseTracker.builder().id(UUID.randomUUID()).name("Other").build();
		TodoTag existing = TodoTag.builder().id(UUID.randomUUID()).expenseTracker(anotherTracker).name("other").active(true).build();
		when(todoTagRepository.findByIdAndIsDeletedFalse(existing.getId())).thenReturn(Optional.of(existing));

		assertThatThrownBy(() -> todoTagService.todoTagDelete(user, trackerId, existing.getId()))
				.isInstanceOf(TodoTagNotFoundException.class)
				.hasMessageContaining("does not belong");
		verify(todoTagAssignmentRepository, never()).deleteByTagId(existing.getId());
	}
}