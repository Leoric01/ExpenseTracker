package org.leoric.expensetracker.kanban.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.handler.exceptions.DuplicateKanbanNameException;
import org.leoric.expensetracker.handler.exceptions.DuplicateKanbanStageNameException;
import org.leoric.expensetracker.handler.exceptions.InvalidKanbanException;
import org.leoric.expensetracker.handler.exceptions.KanbanBoardNotFoundException;
import org.leoric.expensetracker.handler.exceptions.KanbanStageNotFoundException;
import org.leoric.expensetracker.kanban.dtos.KanbanBoardDetailResponseDto;
import org.leoric.expensetracker.kanban.dtos.KanbanBoardReorderItemDto;
import org.leoric.expensetracker.kanban.dtos.KanbanBoardReorderRequestDto;
import org.leoric.expensetracker.kanban.dtos.KanbanBoardResponseDto;
import org.leoric.expensetracker.kanban.dtos.KanbanBoardUpsertRequestDto;
import org.leoric.expensetracker.kanban.dtos.KanbanStageReorderItemDto;
import org.leoric.expensetracker.kanban.dtos.KanbanStageReorderRequestDto;
import org.leoric.expensetracker.kanban.dtos.KanbanStageResponseDto;
import org.leoric.expensetracker.kanban.dtos.KanbanStageUpsertRequestDto;
import org.leoric.expensetracker.kanban.mapstruct.KanbanMapper;
import org.leoric.expensetracker.kanban.models.KanbanBoard;
import org.leoric.expensetracker.kanban.models.KanbanCard;
import org.leoric.expensetracker.kanban.models.KanbanStage;
import org.leoric.expensetracker.kanban.repositories.KanbanBoardRepository;
import org.leoric.expensetracker.kanban.repositories.KanbanCardRepository;
import org.leoric.expensetracker.kanban.repositories.KanbanStageRepository;
import org.leoric.expensetracker.kanban.services.interfaces.KanbanBoardService;
import org.leoric.expensetracker.utils.OrderSequenceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class KanbanBoardServiceImpl implements KanbanBoardService {

	private final KanbanBoardRepository kanbanBoardRepository;
	private final KanbanStageRepository kanbanStageRepository;
	private final KanbanCardRepository kanbanCardRepository;
	private final ExpenseTrackerRepository expenseTrackerRepository;
	private final KanbanMapper kanbanMapper;

	@Override
	@Transactional
	public KanbanBoardResponseDto kanbanBoardCreate(User currentUser, UUID trackerId, KanbanBoardUpsertRequestDto request) {
		ExpenseTracker tracker = getTrackerOrThrow(trackerId);
		String normalizedName = normalizeName(request.name(), "Board name is required");
		if (kanbanBoardRepository.existsByExpenseTrackerIdAndNameIgnoreCaseAndIsDeletedFalse(trackerId, normalizedName)) {
			throw new DuplicateKanbanNameException("Kanban board with name '%s' already exists".formatted(normalizedName));
		}

		List<KanbanBoard> existingBoards = new ArrayList<>(
				kanbanBoardRepository.findByExpenseTrackerIdAndIsDeletedFalseOrderByBoardOrderAscNameAsc(trackerId)
		);
		int targetOrder = OrderSequenceUtils.clampTargetOrder(request.boardOrder(), existingBoards.size());

		KanbanBoard board = KanbanBoard.builder()
				.expenseTracker(tracker)
				.name(normalizedName)
				.description(normalizeNullable(request.description()))
				.boardOrder(targetOrder)
				.active(request.active() == null || request.active())
				.build();
		existingBoards.add(targetOrder, board);
		OrderSequenceUtils.normalizeContiguous(existingBoards, KanbanBoard::getBoardOrder, KanbanBoard::setBoardOrder);
		kanbanBoardRepository.saveAll(existingBoards);
		log.info("User {} created kanban board '{}' in tracker '{}'", currentUser.getEmail(), board.getName(), trackerId);
		return kanbanMapper.toBoardResponse(board);
	}

	@Override
	@Transactional(readOnly = true)
	public List<KanbanBoardResponseDto> kanbanBoardFindAll(User currentUser, UUID trackerId) {
		return kanbanBoardRepository.findByExpenseTrackerIdAndIsDeletedFalseOrderByBoardOrderAscNameAsc(trackerId)
				.stream()
				.map(kanbanMapper::toBoardResponse)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public KanbanBoardDetailResponseDto kanbanBoardFindById(User currentUser, UUID trackerId, UUID boardId) {
		KanbanBoard board = getBoardOrThrow(boardId);
		assertBoardBelongsToTracker(board, trackerId);
		List<KanbanStageResponseDto> stages = kanbanStageRepository
				.findByBoardIdAndIsDeletedFalseOrderByStageOrderAscNameAsc(boardId)
				.stream()
				.map(kanbanMapper::toStageResponse)
				.toList();
		return kanbanMapper.toBoardDetailResponse(board, stages);
	}

	@Override
	@Transactional
	public KanbanBoardResponseDto kanbanBoardUpdate(User currentUser, UUID trackerId, UUID boardId, KanbanBoardUpsertRequestDto request) {
		KanbanBoard board = getBoardOrThrow(boardId);
		assertBoardBelongsToTracker(board, trackerId);
		String normalizedName = normalizeName(request.name(), "Board name is required");
		if (kanbanBoardRepository.existsByExpenseTrackerIdAndNameIgnoreCaseAndIsDeletedFalseAndIdNot(trackerId, normalizedName, boardId)) {
			throw new DuplicateKanbanNameException("Kanban board with name '%s' already exists".formatted(normalizedName));
		}

		board.setName(normalizedName);
		board.setDescription(normalizeNullable(request.description()));
		if (request.active() != null) {
			board.setActive(request.active());
		}

		List<KanbanBoard> boards = new ArrayList<>(
				kanbanBoardRepository.findByExpenseTrackerIdAndIsDeletedFalseOrderByBoardOrderAscNameAsc(trackerId)
		);
		boards.removeIf(b -> b.getId().equals(boardId));
		int targetOrder = OrderSequenceUtils.clampTargetOrder(
				request.boardOrder() == null ? board.getBoardOrder() : request.boardOrder(),
				boards.size()
		);
		boards.add(targetOrder, board);
		OrderSequenceUtils.normalizeContiguous(boards, KanbanBoard::getBoardOrder, KanbanBoard::setBoardOrder);
		kanbanBoardRepository.saveAll(boards);
		return kanbanMapper.toBoardResponse(board);
	}

	@Override
	@Transactional
	public void kanbanBoardReorder(User currentUser, UUID trackerId, KanbanBoardReorderRequestDto request) {
		if (request.items() == null || request.items().isEmpty()) {
			throw new InvalidKanbanException("Reorder payload must contain at least one item");
		}

		Set<UUID> ids = request.items().stream().map(KanbanBoardReorderItemDto::boardId).collect(Collectors.toSet());
		if (ids.size() != request.items().size()) {
			throw new InvalidKanbanException("Reorder payload contains duplicate board ids");
		}

		List<KanbanBoard> boards = kanbanBoardRepository.findByExpenseTrackerIdAndIdInAndIsDeletedFalse(trackerId, ids);
		if (boards.size() != ids.size()) {
			throw new KanbanBoardNotFoundException("Some boards were not found on this expense tracker");
		}

		int trackerBoardCount = kanbanBoardRepository.findByExpenseTrackerIdAndIsDeletedFalseOrderByBoardOrderAscNameAsc(trackerId).size();
		if (ids.size() != trackerBoardCount) {
			throw new InvalidKanbanException("Reorder payload must contain all boards on this expense tracker");
		}

		OrderSequenceUtils.validateContiguous(request.items(), KanbanBoardReorderItemDto::boardOrder, "boardOrder");

		Map<UUID, KanbanBoard> byId = boards.stream().collect(Collectors.toMap(KanbanBoard::getId, Function.identity()));
		for (KanbanBoardReorderItemDto item : request.items()) {
			byId.get(item.boardId()).setBoardOrder(item.boardOrder());
		}
		kanbanBoardRepository.saveAll(boards);
	}

	@Override
	@Transactional
	public void kanbanBoardDelete(User currentUser, UUID trackerId, UUID boardId) {
		KanbanBoard board = getBoardOrThrow(boardId);
		assertBoardBelongsToTracker(board, trackerId);
		board.setDeleted(true);
		board.setActive(false);
		kanbanBoardRepository.save(board);

		List<KanbanBoard> remainingBoards = kanbanBoardRepository.findByExpenseTrackerIdAndIsDeletedFalseOrderByBoardOrderAscNameAsc(trackerId);
		OrderSequenceUtils.normalizeContiguous(remainingBoards, KanbanBoard::getBoardOrder, KanbanBoard::setBoardOrder);
		kanbanBoardRepository.saveAll(remainingBoards);
	}

	@Override
	@Transactional
	public KanbanStageResponseDto kanbanStageCreate(User currentUser, UUID trackerId, UUID boardId, KanbanStageUpsertRequestDto request) {
		KanbanBoard board = getBoardOrThrow(boardId);
		assertBoardBelongsToTracker(board, trackerId);
		String normalizedName = normalizeName(request.name(), "Stage name is required");
		if (kanbanStageRepository.existsByBoardIdAndNameIgnoreCaseAndIsDeletedFalse(boardId, normalizedName)) {
			throw new DuplicateKanbanStageNameException("Kanban stage with name '%s' already exists".formatted(normalizedName));
		}

		List<KanbanStage> existingStages = new ArrayList<>(
				kanbanStageRepository.findByBoardIdAndIsDeletedFalseOrderByStageOrderAscNameAsc(boardId)
		);
		int targetOrder = OrderSequenceUtils.clampTargetOrder(request.stageOrder(), existingStages.size());

		KanbanStage stage = KanbanStage.builder()
				.board(board)
				.name(normalizedName)
				.stageOrder(targetOrder)
				.color(normalizeColor(request.color()))
				.visibleOnBoard(request.visibleOnBoard() == null || request.visibleOnBoard())
				.active(request.active() == null || request.active())
				.build();

		existingStages.add(targetOrder, stage);
		OrderSequenceUtils.normalizeContiguous(existingStages, KanbanStage::getStageOrder, KanbanStage::setStageOrder);
		kanbanStageRepository.saveAll(existingStages);
		return kanbanMapper.toStageResponse(stage);
	}

	@Override
	@Transactional(readOnly = true)
	public List<KanbanStageResponseDto> kanbanStageFindAll(User currentUser, UUID trackerId, UUID boardId) {
		KanbanBoard board = getBoardOrThrow(boardId);
		assertBoardBelongsToTracker(board, trackerId);
		return kanbanStageRepository.findByBoardIdAndIsDeletedFalseOrderByStageOrderAscNameAsc(boardId)
				.stream()
				.map(kanbanMapper::toStageResponse)
				.toList();
	}

	@Override
	@Transactional
	public KanbanStageResponseDto kanbanStageUpdate(User currentUser, UUID trackerId, UUID boardId, UUID stageId, KanbanStageUpsertRequestDto request) {
		KanbanStage stage = getStageOrThrow(stageId);
		assertStageBelongsToBoard(stage, boardId, trackerId);
		String normalizedName = normalizeName(request.name(), "Stage name is required");
		if (kanbanStageRepository.existsByBoardIdAndNameIgnoreCaseAndIsDeletedFalseAndIdNot(boardId, normalizedName, stageId)) {
			throw new DuplicateKanbanStageNameException("Kanban stage with name '%s' already exists".formatted(normalizedName));
		}

		stage.setName(normalizedName);
		stage.setColor(normalizeColor(request.color()));
		if (request.visibleOnBoard() != null) {
			stage.setVisibleOnBoard(request.visibleOnBoard());
		}
		if (request.active() != null) {
			stage.setActive(request.active());
		}

		List<KanbanStage> stages = new ArrayList<>(
				kanbanStageRepository.findByBoardIdAndIsDeletedFalseOrderByStageOrderAscNameAsc(boardId)
		);
		stages.removeIf(s -> s.getId().equals(stageId));
		int targetOrder = OrderSequenceUtils.clampTargetOrder(
				request.stageOrder() == null ? stage.getStageOrder() : request.stageOrder(),
				stages.size()
		);
		stages.add(targetOrder, stage);
		OrderSequenceUtils.normalizeContiguous(stages, KanbanStage::getStageOrder, KanbanStage::setStageOrder);
		kanbanStageRepository.saveAll(stages);
		return kanbanMapper.toStageResponse(stage);
	}

	@Override
	@Transactional
	public void kanbanStageReorder(User currentUser, UUID trackerId, UUID boardId, KanbanStageReorderRequestDto request) {
		KanbanBoard board = getBoardOrThrow(boardId);
		assertBoardBelongsToTracker(board, trackerId);
		if (request.items() == null || request.items().isEmpty()) {
			throw new InvalidKanbanException("Reorder payload must contain at least one item");
		}

		Set<UUID> ids = request.items().stream().map(KanbanStageReorderItemDto::stageId).collect(Collectors.toSet());
		if (ids.size() != request.items().size()) {
			throw new InvalidKanbanException("Reorder payload contains duplicate stage ids");
		}

		List<KanbanStage> stages = kanbanStageRepository.findByBoardIdAndIdInAndIsDeletedFalse(boardId, ids);
		if (stages.size() != ids.size()) {
			throw new KanbanStageNotFoundException("Some stages were not found on this board");
		}
		int boardStageCount = kanbanStageRepository.findByBoardIdAndIsDeletedFalseOrderByStageOrderAscNameAsc(boardId).size();
		if (ids.size() != boardStageCount) {
			throw new InvalidKanbanException("Reorder payload must contain all stages on this board");
		}
		OrderSequenceUtils.validateContiguous(request.items(), KanbanStageReorderItemDto::stageOrder, "stageOrder");

		Map<UUID, KanbanStage> byId = stages.stream().collect(Collectors.toMap(KanbanStage::getId, Function.identity()));
		for (KanbanStageReorderItemDto item : request.items()) {
			byId.get(item.stageId()).setStageOrder(item.stageOrder());
		}
		kanbanStageRepository.saveAll(stages);
	}

	@Override
	@Transactional
	public void kanbanStageDelete(User currentUser, UUID trackerId, UUID boardId, UUID stageId) {
		KanbanStage stage = getStageOrThrow(stageId);
		assertStageBelongsToBoard(stage, boardId, trackerId);

		boolean hasCards = kanbanCardRepository.existsByStageBoardIdAndStageId(boardId, stageId);
		if (hasCards) {
			List<KanbanStage> alternativeStages = kanbanStageRepository
					.findByBoardIdAndIsDeletedFalseOrderByStageOrderAscNameAsc(boardId)
					.stream()
					.filter(s -> !s.getId().equals(stageId))
					.filter(KanbanStage::isActive)
					.toList();

			if (alternativeStages.isEmpty()) {
				throw new InvalidKanbanException("Cannot delete stage because there is no other stage to move cards into");
			}

			KanbanStage targetStage = alternativeStages.stream()
					.min(Comparator
							.comparingInt((KanbanStage s) -> Math.abs(defaultIfNull(s.getStageOrder()) - defaultIfNull(stage.getStageOrder())))
							.thenComparing(KanbanStage::getStageOrder)
							.thenComparing(KanbanStage::getName, String.CASE_INSENSITIVE_ORDER)
					)
					.orElseThrow();

			List<KanbanCard> cardsToMove =
					kanbanCardRepository.findByStageBoardIdAndStageId(boardId, stageId);
			for (KanbanCard card : cardsToMove) {
				card.setStage(targetStage);
			}
			kanbanCardRepository.saveAll(cardsToMove);
		}

		stage.setDeleted(true);
		stage.setActive(false);
		kanbanStageRepository.save(stage);

		List<KanbanStage> remainingStages = kanbanStageRepository.findByBoardIdAndIsDeletedFalseOrderByStageOrderAscNameAsc(boardId);
		OrderSequenceUtils.normalizeContiguous(remainingStages, KanbanStage::getStageOrder, KanbanStage::setStageOrder);
		kanbanStageRepository.saveAll(remainingStages);
	}

	private ExpenseTracker getTrackerOrThrow(UUID trackerId) {
		return expenseTrackerRepository.findById(trackerId)
				.orElseThrow(() -> new EntityNotFoundException("ExpenseTracker not found with id: " + trackerId));
	}

	private KanbanBoard getBoardOrThrow(UUID boardId) {
		return kanbanBoardRepository.findByIdAndIsDeletedFalse(boardId)
				.orElseThrow(() -> new KanbanBoardNotFoundException("Kanban board not found with id: " + boardId));
	}

	private KanbanStage getStageOrThrow(UUID stageId) {
		return kanbanStageRepository.findByIdAndIsDeletedFalse(stageId)
				.orElseThrow(() -> new KanbanStageNotFoundException("Kanban stage not found with id: " + stageId));
	}

	private void assertBoardBelongsToTracker(KanbanBoard board, UUID trackerId) {
		if (!board.getExpenseTracker().getId().equals(trackerId)) {
			throw new KanbanBoardNotFoundException("Kanban board does not belong to this expense tracker");
		}
	}

	private void assertStageBelongsToBoard(KanbanStage stage, UUID boardId, UUID trackerId) {
		if (!stage.getBoard().getId().equals(boardId) || !stage.getBoard().getExpenseTracker().getId().equals(trackerId)) {
			throw new KanbanStageNotFoundException("Kanban stage not found on this board");
		}
	}

	private String normalizeName(String value, String errorMessage) {
		if (value == null || value.isBlank()) {
			throw new InvalidKanbanException(errorMessage);
		}
		return value.trim();
	}

	private String normalizeNullable(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private String normalizeColor(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim().toUpperCase();
	}

	private Integer defaultIfNull(Integer value) {
		return value == null ? 0 : value;
	}
}