package org.leoric.expensetracker.kanban.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.handler.exceptions.InvalidKanbanException;
import org.leoric.expensetracker.kanban.dtos.KanbanTagResponseDto;
import org.leoric.expensetracker.kanban.dtos.KanbanTagUpsertRequestDto;
import org.leoric.expensetracker.kanban.mapstruct.KanbanMapper;
import org.leoric.expensetracker.kanban.models.KanbanTag;
import org.leoric.expensetracker.kanban.repositories.KanbanTagRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KanbanTagServiceImplTest {

	@Mock
	private KanbanTagRepository kanbanTagRepository;
	@Mock
	private ExpenseTrackerRepository expenseTrackerRepository;
	@Mock
	private KanbanMapper kanbanMapper;

	@InjectMocks
	private KanbanTagServiceImpl service;

	@Test
	void kanbanTagCreateBulk_shouldCreateAllItems() {
		UUID trackerId = UUID.randomUUID();
		User user = User.builder().id(UUID.randomUUID()).email("kanban.tag@test.com").build();

		ExpenseTracker tracker = ExpenseTracker.builder().id(trackerId).build();
		when(expenseTrackerRepository.findById(trackerId)).thenReturn(Optional.of(tracker));
		when(kanbanTagRepository.findByExpenseTrackerIdAndIsDeletedFalseOrderByTagOrderAscNameAsc(trackerId)).thenReturn(List.of());

		when(kanbanTagRepository.findByExpenseTrackerIdAndNameIgnoreCaseAndIsDeletedFalse(trackerId, "java")).thenReturn(Optional.empty());
		when(kanbanTagRepository.findByExpenseTrackerIdAndNameIgnoreCaseAndIsDeletedFalse(trackerId, "devops")).thenReturn(Optional.empty());
		when(kanbanTagRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

		when(kanbanMapper.toTagResponse(any(KanbanTag.class))).thenAnswer(invocation -> {
			KanbanTag tag = invocation.getArgument(0);
			return new KanbanTagResponseDto(tag.getId(), tag.getName(), tag.getColor(), tag.getTagOrder(), tag.isActive(), null, null);
		});

		List<KanbanTagResponseDto> response = service.kanbanTagCreateBulk(
				user,
				trackerId,
				List.of(
						new KanbanTagUpsertRequestDto("java", "#00FF00", null, true),
						new KanbanTagUpsertRequestDto("devops", "#112233", null, true)
				)
		);

		assertThat(response).hasSize(2);
		assertThat(response.get(0).name()).isEqualTo("java");
		assertThat(response.get(1).name()).isEqualTo("devops");
		verify(kanbanTagRepository, times(2)).saveAll(any());
	}

	@Test
	void kanbanTagCreateBulk_shouldRejectEmptyPayload() {
		User user = User.builder().id(UUID.randomUUID()).email("kanban.tag@test.com").build();
		UUID trackerId = UUID.randomUUID();

		assertThatThrownBy(() -> service.kanbanTagCreateBulk(user, trackerId, List.of()))
				.isInstanceOf(InvalidKanbanException.class)
				.hasMessageContaining("at least one item");

		verify(kanbanTagRepository, never()).save(any(KanbanTag.class));
	}
}