package org.leoric.expensetracker.kanban.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.handler.exceptions.InvalidKanbanException;
import org.leoric.expensetracker.image.services.interfaces.ImageService;
import org.leoric.expensetracker.kanban.dtos.KanbanCardResponseDto;
import org.leoric.expensetracker.kanban.mapstruct.KanbanMapper;
import org.leoric.expensetracker.kanban.models.KanbanBoard;
import org.leoric.expensetracker.kanban.models.KanbanCard;
import org.leoric.expensetracker.kanban.models.KanbanStage;
import org.leoric.expensetracker.kanban.repositories.KanbanBoardRepository;
import org.leoric.expensetracker.kanban.repositories.KanbanCardTagAssignmentRepository;
import org.leoric.expensetracker.kanban.repositories.KanbanCardRepository;
import org.leoric.expensetracker.kanban.repositories.KanbanStageRepository;
import org.leoric.expensetracker.kanban.repositories.KanbanTagRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KanbanCardServiceImplTest {

	@Mock
	private KanbanCardRepository kanbanCardRepository;
	@Mock
	private KanbanBoardRepository kanbanBoardRepository;
	@Mock
	private KanbanStageRepository kanbanStageRepository;
	@Mock
	private KanbanTagRepository kanbanTagRepository;
	@Mock
	private KanbanCardTagAssignmentRepository kanbanCardTagAssignmentRepository;
	@Mock
	private ImageService imageService;
	@Mock
	private KanbanMapper kanbanMapper;

	@InjectMocks
	private KanbanCardServiceImpl service;

	@Test
	void kanbanCardUploadImage_shouldPersistUploadedCloudinaryUrl() {
		UUID trackerId = UUID.randomUUID();
		UUID boardId = UUID.randomUUID();
		UUID cardId = UUID.randomUUID();
		User user = User.builder().id(UUID.randomUUID()).email("kanban@test.com").build();

		ExpenseTracker tracker = ExpenseTracker.builder().id(trackerId).build();
		KanbanBoard board = KanbanBoard.builder().id(boardId).expenseTracker(tracker).build();
		KanbanStage stage = KanbanStage.builder().id(UUID.randomUUID()).board(board).name("Todo").stageOrder(0).build();
		KanbanCard card = KanbanCard.builder()
				.id(cardId)
				.board(board)
				.stage(stage)
				.title("Task")
				.priority(3)
				.dueDate(LocalDate.now())
				.cardOrder(0)
				.active(true)
				.build();

		when(kanbanCardRepository.findByIdAndIsDeletedFalse(cardId)).thenReturn(Optional.of(card));
		when(imageService.uploadImage(any(), eq("expense-tracker/kanban")))
				.thenReturn("https://res.cloudinary.com/demo/image/upload/v1/kanban.png");
		when(kanbanCardRepository.findByBoardIdAndParentIdInAndIsDeletedFalse(boardId, List.of(cardId))).thenReturn(List.of());
		when(kanbanCardTagAssignmentRepository.findAllByCardIdsWithTag(List.of(cardId))).thenReturn(List.of());
		when(kanbanMapper.toCardResponse(eq(card), eq(List.of()), eq(List.of()))).thenReturn(
				new KanbanCardResponseDto(cardId, stage.getId(), null, "Task", null,
						"https://res.cloudinary.com/demo/image/upload/v1/kanban.png", 3, card.getDueDate(), 0, true, List.of(), List.of(), null, null)
		);

		MockMultipartFile file = new MockMultipartFile("file", "kanban.png", "image/png", new byte[]{1, 2});
		KanbanCardResponseDto response = service.kanbanCardUploadImage(user, trackerId, boardId, cardId, file);

		assertThat(card.getImageUrl()).isEqualTo("https://res.cloudinary.com/demo/image/upload/v1/kanban.png");
		assertThat(response.imageUrl()).isEqualTo("https://res.cloudinary.com/demo/image/upload/v1/kanban.png");
		verify(kanbanCardRepository).save(card);
	}

	@Test
	void kanbanCardDeleteImage_shouldKeepDbReferenceWhenCloudinaryDeleteFails() {
		UUID trackerId = UUID.randomUUID();
		UUID boardId = UUID.randomUUID();
		UUID cardId = UUID.randomUUID();
		User user = User.builder().id(UUID.randomUUID()).email("kanban@test.com").build();

		ExpenseTracker tracker = ExpenseTracker.builder().id(trackerId).build();
		KanbanBoard board = KanbanBoard.builder().id(boardId).expenseTracker(tracker).build();
		KanbanStage stage = KanbanStage.builder().id(UUID.randomUUID()).board(board).name("Todo").stageOrder(0).build();
		KanbanCard card = KanbanCard.builder().id(cardId).board(board).stage(stage).title("Task")
				.imageUrl("https://res.cloudinary.com/demo/image/upload/v1/kanban.png")
				.priority(2).cardOrder(0).active(true).build();

		when(kanbanCardRepository.findByIdAndIsDeletedFalse(cardId)).thenReturn(Optional.of(card));
		when(imageService.deleteImageByUrl(card.getImageUrl())).thenReturn(false);

		assertThatThrownBy(() -> service.kanbanCardDeleteImage(user, trackerId, boardId, cardId))
				.isInstanceOf(InvalidKanbanException.class)
				.hasMessageContaining("Cloudinary");

		assertThat(card.getImageUrl()).isEqualTo("https://res.cloudinary.com/demo/image/upload/v1/kanban.png");
		verify(kanbanCardRepository, never()).save(card);
	}
}