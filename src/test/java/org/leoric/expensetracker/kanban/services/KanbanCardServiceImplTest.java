package org.leoric.expensetracker.kanban.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.handler.exceptions.InvalidKanbanException;
import org.leoric.expensetracker.image.services.interfaces.ImageService;
import org.leoric.expensetracker.kanban.dtos.KanbanCardReorderItemDto;
import org.leoric.expensetracker.kanban.dtos.KanbanCardReorderRequestDto;
import org.leoric.expensetracker.kanban.dtos.KanbanCardResponseDto;
import org.leoric.expensetracker.kanban.dtos.KanbanChecklistItemToggleRequestDto;
import org.leoric.expensetracker.kanban.mapstruct.KanbanMapper;
import org.leoric.expensetracker.kanban.models.KanbanBoard;
import org.leoric.expensetracker.kanban.models.KanbanCard;
import org.leoric.expensetracker.kanban.models.KanbanChecklistItem;
import org.leoric.expensetracker.kanban.models.KanbanStage;
import org.leoric.expensetracker.kanban.repositories.KanbanBoardRepository;
import org.leoric.expensetracker.kanban.repositories.KanbanCardRepository;
import org.leoric.expensetracker.kanban.repositories.KanbanCardTagAssignmentRepository;
import org.leoric.expensetracker.kanban.repositories.KanbanChecklistItemRepository;
import org.leoric.expensetracker.kanban.repositories.KanbanStageRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KanbanCardServiceImplTest {

	@Mock
	private KanbanBoardRepository kanbanBoardRepository;
	@Mock
	private KanbanCardRepository kanbanCardRepository;
	@Mock
	private KanbanStageRepository kanbanStageRepository;
	@Mock
	private KanbanChecklistItemRepository kanbanChecklistItemRepository;
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
				.stage(stage)
				.title("Task")
				.priority(3)
				.dueDate(LocalDate.now())
				.cardOrder(0)
				.build();

		when(kanbanCardRepository.findById(cardId)).thenReturn(Optional.of(card));
		when(imageService.uploadImage(any(), eq("expense-tracker/kanban")))
				.thenReturn("https://res.cloudinary.com/demo/image/upload/v1/kanban.png");
		when(kanbanCardTagAssignmentRepository.findAllByCardIdsWithTag(List.of(cardId))).thenReturn(List.of());
		when(kanbanChecklistItemRepository.findByCardIdOrderByItemOrderAscTitleAsc(cardId)).thenReturn(List.of());
		when(kanbanMapper.toCardResponse(eq(card), eq(List.of()), eq(List.of()))).thenReturn(
				new KanbanCardResponseDto(cardId, stage.getId(), "Task", null,
				                          "https://res.cloudinary.com/demo/image/upload/v1/kanban.png", 3, card.getDueDate(), null, 0, List.of(), List.of(), null, null)
		);

		MockMultipartFile file = new MockMultipartFile("file", "kanban.png", "image/png", new byte[]{1, 2});
		KanbanCardResponseDto response = service.kanbanCardUploadImage(user, trackerId, boardId, cardId, file);

		assertThat(card.getImageUrl()).isEqualTo("https://res.cloudinary.com/demo/image/upload/v1/kanban.png");
		assertThat(response.imageUrl()).isEqualTo("https://res.cloudinary.com/demo/image/upload/v1/kanban.png");
		verify(kanbanCardRepository).save(card);
	}

	@Test
	void kanbanCardMove_shouldSetMovedCardOrderToZeroAndShiftTargetStage() {
		UUID trackerId = UUID.randomUUID();
		User user = User.builder().id(UUID.randomUUID()).email("kanban@test.com").build();

		ExpenseTracker tracker = ExpenseTracker.builder().id(trackerId).build();
		KanbanBoard board = KanbanBoard.builder().id(UUID.randomUUID()).expenseTracker(tracker).build();
		KanbanStage fromStage = KanbanStage.builder().id(UUID.randomUUID()).board(board).name("Backlog").stageOrder(0).build();
		KanbanStage toStage = KanbanStage.builder().id(UUID.randomUUID()).board(board).name("Doing").stageOrder(1).build();

		KanbanCard moved = KanbanCard.builder().id(UUID.randomUUID()).stage(fromStage).title("Moved").cardOrder(2).build();
		KanbanCard targetA = KanbanCard.builder().id(UUID.randomUUID()).stage(toStage).title("A").cardOrder(0).build();
		KanbanCard targetB = KanbanCard.builder().id(UUID.randomUUID()).stage(toStage).title("B").cardOrder(1).build();

		when(kanbanCardRepository.findById(moved.getId())).thenReturn(Optional.of(moved));
		when(kanbanStageRepository.findByIdAndIsDeletedFalse(toStage.getId())).thenReturn(Optional.of(toStage));
		when(kanbanCardRepository.findByStageIdOrderByCardOrderAscTitleAsc(fromStage.getId())).thenReturn(List.of(moved));
		when(kanbanCardRepository.findByStageIdOrderByCardOrderAscTitleAsc(toStage.getId())).thenReturn(List.of(targetA, targetB));
		when(kanbanCardTagAssignmentRepository.findAllByCardIdsWithTag(List.of(moved.getId()))).thenReturn(List.of());
		when(kanbanChecklistItemRepository.findByCardIdOrderByItemOrderAscTitleAsc(moved.getId())).thenReturn(List.of());
		when(kanbanMapper.toCardResponse(eq(moved), eq(List.of()), eq(List.of()))).thenReturn(
				new KanbanCardResponseDto(moved.getId(), toStage.getId(), "Moved", null, null, 0, null, null, 0, List.of(), List.of(), null, null)
		);

		KanbanCardResponseDto response = service.kanbanCardMove(user, trackerId, moved.getId(), toStage.getId());

		assertThat(response.stageId()).isEqualTo(toStage.getId());
		assertThat(moved.getCardOrder()).isEqualTo(0);
		assertThat(targetA.getCardOrder()).isEqualTo(1);
		assertThat(targetB.getCardOrder()).isEqualTo(2);
		verify(kanbanCardRepository, org.mockito.Mockito.times(2)).saveAll(any());
	}

	@Test
	void kanbanChecklistItemToggle_shouldSetAndClearCompletedDate() {
		UUID trackerId = UUID.randomUUID();
		UUID boardId = UUID.randomUUID();
		UUID cardId = UUID.randomUUID();
		UUID itemId = UUID.randomUUID();
		User user = User.builder().id(UUID.randomUUID()).email("kanban@test.com").build();

		ExpenseTracker tracker = ExpenseTracker.builder().id(trackerId).build();
		KanbanBoard board = KanbanBoard.builder().id(boardId).expenseTracker(tracker).build();
		KanbanStage stage = KanbanStage.builder().id(UUID.randomUUID()).board(board).name("Todo").stageOrder(0).build();
		KanbanCard card = KanbanCard.builder().id(cardId).stage(stage).title("Task").cardOrder(0).build();
		KanbanChecklistItem item = KanbanChecklistItem.builder().id(itemId).card(card).title("Subtask").itemOrder(0).completed(false).build();

		when(kanbanCardRepository.findById(cardId)).thenReturn(Optional.of(card));
		when(kanbanChecklistItemRepository.findByCardIdAndId(cardId, itemId)).thenReturn(Optional.of(item));
		when(kanbanCardTagAssignmentRepository.findAllByCardIdsWithTag(List.of(cardId))).thenReturn(List.of());
		when(kanbanChecklistItemRepository.findByCardIdOrderByItemOrderAscTitleAsc(cardId)).thenReturn(List.of(item));
		when(kanbanMapper.toCardResponse(eq(card), eq(List.of()), any())).thenReturn(
				new KanbanCardResponseDto(cardId, stage.getId(), "Task", null, null, 0, null, null, 0, List.of(), List.of(), null, null)
		);

		service.kanbanChecklistItemToggle(user, trackerId, boardId, cardId, itemId, new KanbanChecklistItemToggleRequestDto(true));
		assertThat(item.isCompleted()).isTrue();
		assertThat(item.getCompletedDate()).isNotNull();

		service.kanbanChecklistItemToggle(user, trackerId, boardId, cardId, itemId, new KanbanChecklistItemToggleRequestDto(false));
		assertThat(item.isCompleted()).isFalse();
		assertThat(item.getCompletedDate()).isNull();
	}

	@Test
	void kanbanCardReorder_shouldApplyRequestedCardOrdersWithinStage() {
		UUID trackerId = UUID.randomUUID();
		UUID boardId = UUID.randomUUID();
		User user = User.builder().id(UUID.randomUUID()).email("kanban@test.com").build();

		ExpenseTracker tracker = ExpenseTracker.builder().id(trackerId).build();
		KanbanBoard board = KanbanBoard.builder().id(boardId).expenseTracker(tracker).build();
		KanbanStage stage = KanbanStage.builder().id(UUID.randomUUID()).board(board).name("Doing").stageOrder(1).build();

		KanbanCard first = KanbanCard.builder().id(UUID.randomUUID()).stage(stage).title("A").cardOrder(0).build();
		KanbanCard second = KanbanCard.builder().id(UUID.randomUUID()).stage(stage).title("B").cardOrder(1).build();

		when(kanbanBoardRepository.findByIdAndIsDeletedFalse(boardId)).thenReturn(Optional.of(board));
		when(kanbanStageRepository.findByIdAndIsDeletedFalse(stage.getId())).thenReturn(Optional.of(stage));
		when(kanbanCardRepository.findByStageIdAndIdIn(eq(stage.getId()), any())).thenReturn(List.of(first, second));
		when(kanbanCardRepository.findByStageIdOrderByCardOrderAscTitleAsc(stage.getId())).thenReturn(List.of(first, second));

		service.kanbanCardReorder(
				user,
				trackerId,
				boardId,
				new KanbanCardReorderRequestDto(
						stage.getId(),
						List.of(
								new KanbanCardReorderItemDto(first.getId(), 1),
								new KanbanCardReorderItemDto(second.getId(), 0)
						)
				)
		);

		assertThat(first.getCardOrder()).isEqualTo(1);
		assertThat(second.getCardOrder()).isEqualTo(0);
		verify(kanbanCardRepository).saveAll(List.of(first, second));
	}

	@Test
	void kanbanCardReorder_shouldRejectWhenPayloadDoesNotContainAllStageCards() {
		UUID trackerId = UUID.randomUUID();
		UUID boardId = UUID.randomUUID();
		User user = User.builder().id(UUID.randomUUID()).email("kanban@test.com").build();

		ExpenseTracker tracker = ExpenseTracker.builder().id(trackerId).build();
		KanbanBoard board = KanbanBoard.builder().id(boardId).expenseTracker(tracker).build();
		KanbanStage stage = KanbanStage.builder().id(UUID.randomUUID()).board(board).name("Doing").stageOrder(1).build();

		KanbanCard first = KanbanCard.builder().id(UUID.randomUUID()).stage(stage).title("A").cardOrder(0).build();
		KanbanCard second = KanbanCard.builder().id(UUID.randomUUID()).stage(stage).title("B").cardOrder(1).build();

		when(kanbanBoardRepository.findByIdAndIsDeletedFalse(boardId)).thenReturn(Optional.of(board));
		when(kanbanStageRepository.findByIdAndIsDeletedFalse(stage.getId())).thenReturn(Optional.of(stage));
		when(kanbanCardRepository.findByStageIdAndIdIn(eq(stage.getId()), any())).thenReturn(List.of(first));
		when(kanbanCardRepository.findByStageIdOrderByCardOrderAscTitleAsc(stage.getId())).thenReturn(List.of(first, second));

		assertThatThrownBy(() -> service.kanbanCardReorder(
				user,
				trackerId,
				boardId,
				new KanbanCardReorderRequestDto(stage.getId(), List.of(new KanbanCardReorderItemDto(first.getId(), 0)))
		))
				.isInstanceOf(InvalidKanbanException.class)
				.hasMessageContaining("all cards");
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
		KanbanCard card = KanbanCard.builder().id(cardId).stage(stage).title("Task")
				.imageUrl("https://res.cloudinary.com/demo/image/upload/v1/kanban.png")
				.priority(2).cardOrder(0).build();

		when(kanbanCardRepository.findById(cardId)).thenReturn(Optional.of(card));
		when(imageService.deleteImageByUrl(card.getImageUrl())).thenReturn(false);

		assertThatThrownBy(() -> service.kanbanCardDeleteImage(user, trackerId, boardId, cardId))
				.isInstanceOf(InvalidKanbanException.class)
				.hasMessageContaining("Cloudinary");

		assertThat(card.getImageUrl()).isEqualTo("https://res.cloudinary.com/demo/image/upload/v1/kanban.png");
		verify(kanbanCardRepository, never()).save(card);
	}
}