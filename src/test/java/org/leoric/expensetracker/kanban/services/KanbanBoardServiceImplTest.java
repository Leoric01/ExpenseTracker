package org.leoric.expensetracker.kanban.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.handler.exceptions.InvalidKanbanException;
import org.leoric.expensetracker.kanban.mapstruct.KanbanMapper;
import org.leoric.expensetracker.kanban.models.KanbanBoard;
import org.leoric.expensetracker.kanban.models.KanbanCard;
import org.leoric.expensetracker.kanban.models.KanbanStage;
import org.leoric.expensetracker.kanban.repositories.KanbanBoardRepository;
import org.leoric.expensetracker.kanban.repositories.KanbanCardRepository;
import org.leoric.expensetracker.kanban.repositories.KanbanStageRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KanbanBoardServiceImplTest {

	@Mock
	private KanbanBoardRepository kanbanBoardRepository;
	@Mock
	private KanbanStageRepository kanbanStageRepository;
	@Mock
	private KanbanCardRepository kanbanCardRepository;
	@Mock
	private ExpenseTrackerRepository expenseTrackerRepository;
	@Mock
	private KanbanMapper kanbanMapper;

	@InjectMocks
	private KanbanBoardServiceImpl service;

	@Test
	void kanbanStageDelete_shouldMoveCardsToNearestStageThenDeleteStage() {
		UUID trackerId = UUID.randomUUID();
		UUID boardId = UUID.randomUUID();
		UUID stageId = UUID.randomUUID();
		User user = User.builder().id(UUID.randomUUID()).email("kanban@test.com").build();

		ExpenseTracker tracker = ExpenseTracker.builder().id(trackerId).build();
		KanbanBoard board = KanbanBoard.builder().id(boardId).expenseTracker(tracker).build();
		KanbanStage targetDelete = KanbanStage.builder().id(stageId).board(board).name("Analyze").stageOrder(5).active(true).build();
		KanbanStage nearest = KanbanStage.builder().id(UUID.randomUUID()).board(board).name("Backlog").stageOrder(4).active(true).build();
		KanbanStage farther = KanbanStage.builder().id(UUID.randomUUID()).board(board).name("Done").stageOrder(10).active(true).build();

		KanbanCard card = KanbanCard.builder().id(UUID.randomUUID()).board(board).stage(targetDelete).title("Task").build();

		when(kanbanStageRepository.findByIdAndIsDeletedFalse(stageId)).thenReturn(Optional.of(targetDelete));
		when(kanbanCardRepository.existsByBoardIdAndStageIdAndIsDeletedFalse(boardId, stageId)).thenReturn(true);
		when(kanbanStageRepository.findByBoardIdAndIsDeletedFalseOrderByStageOrderAscNameAsc(boardId))
				.thenReturn(List.of(nearest, targetDelete, farther));
		when(kanbanCardRepository.findByBoardIdAndStageIdAndIsDeletedFalse(boardId, stageId)).thenReturn(List.of(card));

		service.kanbanStageDelete(user, trackerId, boardId, stageId);

		assertThat(card.getStage().getId()).isEqualTo(nearest.getId());
		assertThat(targetDelete.isDeleted()).isTrue();
		assertThat(targetDelete.isActive()).isFalse();
		verify(kanbanCardRepository).saveAll(List.of(card));
		verify(kanbanStageRepository).save(targetDelete);
	}

	@Test
	void kanbanStageDelete_shouldFailWhenCardsExistAndNoAlternativeStage() {
		UUID trackerId = UUID.randomUUID();
		UUID boardId = UUID.randomUUID();
		UUID stageId = UUID.randomUUID();
		User user = User.builder().id(UUID.randomUUID()).email("kanban@test.com").build();

		ExpenseTracker tracker = ExpenseTracker.builder().id(trackerId).build();
		KanbanBoard board = KanbanBoard.builder().id(boardId).expenseTracker(tracker).build();
		KanbanStage targetDelete = KanbanStage.builder().id(stageId).board(board).name("Only stage").stageOrder(0).active(true).build();

		when(kanbanStageRepository.findByIdAndIsDeletedFalse(stageId)).thenReturn(Optional.of(targetDelete));
		when(kanbanCardRepository.existsByBoardIdAndStageIdAndIsDeletedFalse(boardId, stageId)).thenReturn(true);
		when(kanbanStageRepository.findByBoardIdAndIsDeletedFalseOrderByStageOrderAscNameAsc(boardId))
				.thenReturn(List.of(targetDelete));

		assertThatThrownBy(() -> service.kanbanStageDelete(user, trackerId, boardId, stageId))
				.isInstanceOf(InvalidKanbanException.class)
				.hasMessageContaining("no other stage");

		verifyNoMoreInteractions(kanbanBoardRepository, expenseTrackerRepository, kanbanMapper);
	}
}