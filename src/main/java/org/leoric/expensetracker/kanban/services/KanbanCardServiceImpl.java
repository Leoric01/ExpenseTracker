package org.leoric.expensetracker.kanban.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessService;
import org.leoric.expensetracker.handler.exceptions.DuplicateKanbanNameException;
import org.leoric.expensetracker.handler.exceptions.InvalidKanbanException;
import org.leoric.expensetracker.handler.exceptions.KanbanBoardNotFoundException;
import org.leoric.expensetracker.handler.exceptions.KanbanCardNotFoundException;
import org.leoric.expensetracker.handler.exceptions.KanbanStageNotFoundException;
import org.leoric.expensetracker.handler.exceptions.KanbanTagNotFoundException;
import org.leoric.expensetracker.image.services.interfaces.ImageService;
import org.leoric.expensetracker.kanban.dtos.KanbanBoardSnapshotResponseDto;
import org.leoric.expensetracker.kanban.dtos.KanbanCardResponseDto;
import org.leoric.expensetracker.kanban.dtos.KanbanCardUpsertRequestDto;
import org.leoric.expensetracker.kanban.dtos.KanbanChecklistItemReorderItemDto;
import org.leoric.expensetracker.kanban.dtos.KanbanChecklistItemReorderRequestDto;
import org.leoric.expensetracker.kanban.dtos.KanbanChecklistItemResponseDto;
import org.leoric.expensetracker.kanban.dtos.KanbanChecklistItemToggleRequestDto;
import org.leoric.expensetracker.kanban.dtos.KanbanChecklistItemUpsertRequestDto;
import org.leoric.expensetracker.kanban.dtos.KanbanStageCardsResponseDto;
import org.leoric.expensetracker.kanban.dtos.KanbanTagResponseDto;
import org.leoric.expensetracker.kanban.mapstruct.KanbanMapper;
import org.leoric.expensetracker.kanban.models.KanbanBoard;
import org.leoric.expensetracker.kanban.models.KanbanCard;
import org.leoric.expensetracker.kanban.models.KanbanChecklistItem;
import org.leoric.expensetracker.kanban.models.KanbanCardStageHistory;
import org.leoric.expensetracker.kanban.models.KanbanCardTagAssignment;
import org.leoric.expensetracker.kanban.models.KanbanStage;
import org.leoric.expensetracker.kanban.models.KanbanTag;
import org.leoric.expensetracker.kanban.repositories.KanbanBoardRepository;
import org.leoric.expensetracker.kanban.repositories.KanbanCardRepository;
import org.leoric.expensetracker.kanban.repositories.KanbanChecklistItemRepository;
import org.leoric.expensetracker.kanban.repositories.KanbanCardTagAssignmentRepository;
import org.leoric.expensetracker.kanban.repositories.KanbanStageRepository;
import org.leoric.expensetracker.kanban.repositories.KanbanTagRepository;
import org.leoric.expensetracker.kanban.services.interfaces.KanbanCardService;
import org.leoric.expensetracker.utils.OrderSequenceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_MEMBER;
import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_OWNER;

@Service
@Slf4j
@RequiredArgsConstructor
public class KanbanCardServiceImpl implements KanbanCardService {

	private static final Set<String> ALLOWED_IMAGE_CONTENT_TYPES = Set.of(
			"image/jpeg",
			"image/png",
			"image/webp",
			"image/heic"
	);
	private static final Comparator<KanbanCard> CARD_ORDER = Comparator
			.comparing((KanbanCard c) -> c.getCardOrder() == null ? 0 : c.getCardOrder())
			.thenComparing(KanbanCard::getTitle, String.CASE_INSENSITIVE_ORDER);

	private final KanbanCardRepository kanbanCardRepository;
	private final KanbanBoardRepository kanbanBoardRepository;
	private final KanbanStageRepository kanbanStageRepository;
	private final KanbanTagRepository kanbanTagRepository;
	private final KanbanChecklistItemRepository kanbanChecklistItemRepository;
	private final KanbanCardTagAssignmentRepository kanbanCardTagAssignmentRepository;
	private final ImageService imageService;
	private final ExpenseTrackerAccessService expenseTrackerAccessService;
	private final KanbanMapper kanbanMapper;

	@Override
	@Transactional
	public KanbanCardResponseDto kanbanCardMove(User currentUser, UUID trackerId, UUID cardId, UUID targetStageId) {
		if (targetStageId == null) {
			throw new InvalidKanbanException("targetStageId is required");
		}

		KanbanCard card = getCardOrThrow(cardId);
		KanbanStage fromStage = card.getStage();
		KanbanStage toStage = getStageOrThrow(targetStageId);

		assertStageBelongsToTracker(toStage, trackerId);
		assertStageBelongsToTracker(fromStage, trackerId);
		assertHasAccessToTracker(currentUser, trackerId);

		if (!fromStage.getId().equals(toStage.getId())) {
			List<KanbanCard> sourceCards = new java.util.ArrayList<>(
					kanbanCardRepository.findByStageIdOrderByCardOrderAscTitleAsc(fromStage.getId())
			);
			sourceCards.removeIf(c -> c.getId().equals(card.getId()));

			List<KanbanCard> targetCards = new java.util.ArrayList<>(
					kanbanCardRepository.findByStageIdOrderByCardOrderAscTitleAsc(toStage.getId())
			);
			targetCards.forEach(c -> c.setCardOrder(defaultIfNull(c.getCardOrder()) + 1));

			card.setStage(toStage);
			card.setCardOrder(0);

			KanbanCardStageHistory history = KanbanCardStageHistory.builder()
					.card(card)
					.fromStage(fromStage)
					.toStage(toStage)
					.changedDate(Instant.now())
					.build();
			card.getStageHistory().add(history);

			if (toStage.isFinalStage() && card.getCompletedDate() == null) {
				card.setCompletedDate(Instant.now());
			}

			OrderSequenceUtils.normalizeContiguous(sourceCards, KanbanCard::getCardOrder, KanbanCard::setCardOrder);
			kanbanCardRepository.saveAll(sourceCards);

			List<KanbanCard> targetWithMoved = new java.util.ArrayList<>();
			targetWithMoved.add(card);
			targetWithMoved.addAll(targetCards);
			OrderSequenceUtils.normalizeContiguous(targetWithMoved, KanbanCard::getCardOrder, KanbanCard::setCardOrder);
			kanbanCardRepository.saveAll(targetWithMoved);
		}

		return toCardResponse(card);
	}

	@Override
	@Transactional
	public KanbanCardResponseDto kanbanCardCreate(User currentUser, UUID trackerId, UUID boardId, KanbanCardUpsertRequestDto request) {
		KanbanBoard board = getBoardOrThrow(boardId);
		assertBoardBelongsToTracker(board, trackerId);
		KanbanStage stage = resolveStage(boardId, request.stageId());
		assertUniqueTitle(boardId, request.title(), null);

		List<KanbanCard> stageCards = new java.util.ArrayList<>(
				kanbanCardRepository.findByStageIdOrderByCardOrderAscTitleAsc(stage.getId())
		);
		int targetOrder = OrderSequenceUtils.clampTargetOrder(request.cardOrder(), stageCards.size());

		KanbanCard card = KanbanCard.builder()
				.stage(stage)
				.title(normalizeTitle(request.title()))
				.description(normalizeNullable(request.description()))
				.imageUrl(normalizeImageUrl(request.imageUrl()))
				.priority(validatePriority(request.priority()))
				.dueDate(request.dueDate())
				.cardOrder(targetOrder)
				.build();
		stageCards.add(targetOrder, card);
		OrderSequenceUtils.normalizeContiguous(stageCards, KanbanCard::getCardOrder, KanbanCard::setCardOrder);
		kanbanCardRepository.saveAll(stageCards);
		replaceCardTags(trackerId, card, request.tagIds());
		return toCardResponse(card);
	}

	@Override
	@Transactional(readOnly = true)
	public KanbanBoardSnapshotResponseDto kanbanCardFindBoardSnapshot(User currentUser, UUID trackerId, UUID boardId, Boolean visibleOnly) {
		KanbanBoard board = getBoardOrThrow(boardId);
		assertBoardBelongsToTracker(board, trackerId);
		boolean onlyVisible = visibleOnly == null || visibleOnly;

		List<KanbanStage> stages = kanbanStageRepository.findByBoardIdAndIsDeletedFalseOrderByStageOrderAscNameAsc(boardId).stream()
				.filter(s -> !onlyVisible || s.isVisibleOnBoard())
				.toList();
		List<KanbanCard> allCards = kanbanCardRepository.findByStageBoardIdOrderByCardOrderAscTitleAsc(boardId);
		Map<UUID, List<KanbanCard>> cardsByStage = allCards.stream().collect(Collectors.groupingBy(c -> c.getStage().getId()));
		Map<UUID, List<KanbanTag>> tagsByCardId = loadTagsByCardIds(allCards.stream().map(KanbanCard::getId).toList());

		List<KanbanStageCardsResponseDto> stageDtos = stages.stream()
				.map(stage -> new KanbanStageCardsResponseDto(
						stage.getId(),
						stage.getName(),
						stage.getStageOrder(),
						stage.getColor(),
						stage.isVisibleOnBoard(),
						buildStageCards(cardsByStage.getOrDefault(stage.getId(), List.of()), tagsByCardId)
				))
				.toList();

		return new KanbanBoardSnapshotResponseDto(board.getId(), board.getName(), stageDtos);
	}

	@Override
	@Transactional(readOnly = true)
	public KanbanCardResponseDto kanbanCardFindById(User currentUser, UUID trackerId, UUID boardId, UUID cardId) {
		KanbanCard card = getCardOrThrow(cardId);
		assertCardBelongsToBoard(card, boardId, trackerId);
		return toCardResponse(card);
	}

	@Override
	@Transactional
	public KanbanCardResponseDto kanbanCardUpdate(User currentUser, UUID trackerId, UUID boardId, UUID cardId, KanbanCardUpsertRequestDto request) {
		KanbanCard card = getCardOrThrow(cardId);
		assertCardBelongsToBoard(card, boardId, trackerId);
		KanbanStage stage = resolveStage(boardId, request.stageId());
		assertUniqueTitle(boardId, request.title(), cardId);

		boolean stageChanged = !card.getStage().getId().equals(stage.getId());
		KanbanStage previousStage = card.getStage();
		card.setStage(stage);
		card.setTitle(normalizeTitle(request.title()));
		card.setDescription(normalizeNullable(request.description()));
		card.setImageUrl(normalizeImageUrl(request.imageUrl()));
		card.setPriority(validatePriority(request.priority()));
		card.setDueDate(request.dueDate());
		if (stageChanged) {
			List<KanbanCard> sourceCards = new java.util.ArrayList<>(
					kanbanCardRepository.findByStageIdOrderByCardOrderAscTitleAsc(previousStage.getId())
			);
			sourceCards.removeIf(c -> c.getId().equals(card.getId()));
			OrderSequenceUtils.normalizeContiguous(sourceCards, KanbanCard::getCardOrder, KanbanCard::setCardOrder);
			kanbanCardRepository.saveAll(sourceCards);

			List<KanbanCard> targetCards = new java.util.ArrayList<>(
					kanbanCardRepository.findByStageIdOrderByCardOrderAscTitleAsc(stage.getId())
			);
			targetCards.forEach(c -> c.setCardOrder(defaultIfNull(c.getCardOrder()) + 1));
			card.setCardOrder(0);
			List<KanbanCard> targetWithUpdated = new java.util.ArrayList<>();
			targetWithUpdated.add(card);
			targetWithUpdated.addAll(targetCards);
			OrderSequenceUtils.normalizeContiguous(targetWithUpdated, KanbanCard::getCardOrder, KanbanCard::setCardOrder);
			kanbanCardRepository.saveAll(targetWithUpdated);
		} else {
			List<KanbanCard> stageCards = new java.util.ArrayList<>(
					kanbanCardRepository.findByStageIdOrderByCardOrderAscTitleAsc(stage.getId())
			);
			stageCards.removeIf(c -> c.getId().equals(card.getId()));
			int targetOrder = OrderSequenceUtils.clampTargetOrder(
					request.cardOrder() == null ? card.getCardOrder() : request.cardOrder(),
					stageCards.size()
			);
			stageCards.add(targetOrder, card);
			OrderSequenceUtils.normalizeContiguous(stageCards, KanbanCard::getCardOrder, KanbanCard::setCardOrder);
			kanbanCardRepository.saveAll(stageCards);
		}

		replaceCardTags(trackerId, card, request.tagIds());
		return toCardResponse(card);
	}

	@Override
	@Transactional
	public KanbanCardResponseDto kanbanChecklistItemCreate(User currentUser, UUID trackerId, UUID boardId, UUID cardId, KanbanChecklistItemUpsertRequestDto request) {
		KanbanCard card = getCardOrThrow(cardId);
		assertCardBelongsToBoard(card, boardId, trackerId);

		List<KanbanChecklistItem> items = new java.util.ArrayList<>(
				kanbanChecklistItemRepository.findByCardIdOrderByItemOrderAscTitleAsc(cardId)
		);
		int targetOrder = request.itemOrder() == null
				? items.size()
				: OrderSequenceUtils.clampTargetOrder(request.itemOrder(), items.size());

		KanbanChecklistItem item = KanbanChecklistItem.builder()
				.card(card)
				.title(normalizeChecklistTitle(request.title()))
				.itemOrder(targetOrder)
				.completed(false)
				.completedDate(null)
				.build();

		items.add(targetOrder, item);
		OrderSequenceUtils.normalizeContiguous(items, KanbanChecklistItem::getItemOrder, KanbanChecklistItem::setItemOrder);
		kanbanChecklistItemRepository.saveAll(items);
		return toCardResponse(card);
	}

	@Override
	@Transactional
	public KanbanCardResponseDto kanbanChecklistItemUpdate(User currentUser, UUID trackerId, UUID boardId, UUID cardId, UUID itemId, KanbanChecklistItemUpsertRequestDto request) {
		KanbanCard card = getCardOrThrow(cardId);
		assertCardBelongsToBoard(card, boardId, trackerId);
		KanbanChecklistItem item = getChecklistItemOrThrow(cardId, itemId);

		item.setTitle(normalizeChecklistTitle(request.title()));

		List<KanbanChecklistItem> items = new java.util.ArrayList<>(
				kanbanChecklistItemRepository.findByCardIdOrderByItemOrderAscTitleAsc(cardId)
		);
		items.removeIf(i -> i.getId().equals(itemId));
		int targetOrder = OrderSequenceUtils.clampTargetOrder(
				request.itemOrder() == null ? item.getItemOrder() : request.itemOrder(),
				items.size()
		);
		items.add(targetOrder, item);
		OrderSequenceUtils.normalizeContiguous(items, KanbanChecklistItem::getItemOrder, KanbanChecklistItem::setItemOrder);
		kanbanChecklistItemRepository.saveAll(items);
		return toCardResponse(card);
	}

	@Override
	@Transactional
	public KanbanCardResponseDto kanbanChecklistItemToggle(User currentUser, UUID trackerId, UUID boardId, UUID cardId, UUID itemId, KanbanChecklistItemToggleRequestDto request) {
		KanbanCard card = getCardOrThrow(cardId);
		assertCardBelongsToBoard(card, boardId, trackerId);
		KanbanChecklistItem item = getChecklistItemOrThrow(cardId, itemId);

		if (Boolean.TRUE.equals(request.completed())) {
			item.setCompleted(true);
			item.setCompletedDate(Instant.now());
		} else {
			item.setCompleted(false);
			item.setCompletedDate(null);
		}
		kanbanChecklistItemRepository.save(item);
		return toCardResponse(card);
	}

	@Override
	@Transactional
	public KanbanCardResponseDto kanbanChecklistItemReorder(User currentUser, UUID trackerId, UUID boardId, UUID cardId, KanbanChecklistItemReorderRequestDto request) {
		KanbanCard card = getCardOrThrow(cardId);
		assertCardBelongsToBoard(card, boardId, trackerId);

		if (request.items() == null || request.items().isEmpty()) {
			throw new InvalidKanbanException("Reorder payload must contain at least one item");
		}

		Set<UUID> ids = request.items().stream().map(KanbanChecklistItemReorderItemDto::itemId).collect(Collectors.toSet());
		if (ids.size() != request.items().size()) {
			throw new InvalidKanbanException("Reorder payload contains duplicate checklist item ids");
		}

		List<KanbanChecklistItem> items = kanbanChecklistItemRepository.findByCardIdAndIdIn(cardId, ids);
		if (items.size() != ids.size()) {
			throw new InvalidKanbanException("Some checklist items were not found on this card");
		}

		int itemCount = kanbanChecklistItemRepository.findByCardIdOrderByItemOrderAscTitleAsc(cardId).size();
		if (ids.size() != itemCount) {
			throw new InvalidKanbanException("Reorder payload must contain all checklist items on this card");
		}

		OrderSequenceUtils.validateContiguous(request.items(), KanbanChecklistItemReorderItemDto::itemOrder, "itemOrder");

		Map<UUID, KanbanChecklistItem> byId = items.stream().collect(Collectors.toMap(KanbanChecklistItem::getId, java.util.function.Function.identity()));
		for (KanbanChecklistItemReorderItemDto reorderItem : request.items()) {
			byId.get(reorderItem.itemId()).setItemOrder(reorderItem.itemOrder());
		}
		kanbanChecklistItemRepository.saveAll(items);
		return toCardResponse(card);
	}

	@Override
	@Transactional
	public void kanbanChecklistItemDelete(User currentUser, UUID trackerId, UUID boardId, UUID cardId, UUID itemId) {
		KanbanCard card = getCardOrThrow(cardId);
		assertCardBelongsToBoard(card, boardId, trackerId);
		KanbanChecklistItem item = getChecklistItemOrThrow(cardId, itemId);
		kanbanChecklistItemRepository.delete(item);

		List<KanbanChecklistItem> remaining = new java.util.ArrayList<>(
				kanbanChecklistItemRepository.findByCardIdOrderByItemOrderAscTitleAsc(cardId)
		);
		OrderSequenceUtils.normalizeContiguous(remaining, KanbanChecklistItem::getItemOrder, KanbanChecklistItem::setItemOrder);
		kanbanChecklistItemRepository.saveAll(remaining);
	}

	@Override
	@Transactional
	public KanbanCardResponseDto kanbanCardUploadImage(User currentUser, UUID trackerId, UUID boardId, UUID cardId, MultipartFile file) {
		KanbanCard card = getCardOrThrow(cardId);
		assertCardBelongsToBoard(card, boardId, trackerId);

		if (file == null || file.isEmpty()) {
			throw new InvalidKanbanException("Image file is required");
		}

		String contentType = file.getContentType();
		if (contentType == null || !ALLOWED_IMAGE_CONTENT_TYPES.contains(contentType)) {
			throw new InvalidKanbanException(
					"Unsupported image type '%s'. Allowed: JPEG, PNG, WEBP, HEIC".formatted(contentType)
			);
		}

		String imageUrl = imageService.uploadImage(file, "expense-tracker/kanban");
		card.setImageUrl(imageUrl);
		kanbanCardRepository.save(card);
		return toCardResponse(card);
	}

	@Override
	@Transactional
	public void kanbanCardDeleteImage(User currentUser, UUID trackerId, UUID boardId, UUID cardId) {
		KanbanCard card = getCardOrThrow(cardId);
		assertCardBelongsToBoard(card, boardId, trackerId);

		if (card.getImageUrl() == null || card.getImageUrl().isBlank()) {
			return;
		}

		boolean deleted = imageService.deleteImageByUrl(card.getImageUrl());
		if (!deleted) {
			throw new InvalidKanbanException("Failed to delete image from Cloudinary. Kanban card image reference was not changed");
		}

		card.setImageUrl(null);
		kanbanCardRepository.save(card);
	}

	@Override
	@Transactional
	public void kanbanCardDelete(User currentUser, UUID trackerId, UUID boardId, UUID cardId) {
		KanbanCard card = getCardOrThrow(cardId);
		assertCardBelongsToBoard(card, boardId, trackerId);
		UUID stageId = card.getStage().getId();
		kanbanCardRepository.delete(card);

		List<KanbanCard> remaining = new java.util.ArrayList<>(
				kanbanCardRepository.findByStageIdOrderByCardOrderAscTitleAsc(stageId)
		);
		OrderSequenceUtils.normalizeContiguous(remaining, KanbanCard::getCardOrder, KanbanCard::setCardOrder);
		kanbanCardRepository.saveAll(remaining);
	}

	private List<KanbanCardResponseDto> buildStageCards(List<KanbanCard> stageCards, Map<UUID, List<KanbanTag>> tagsByCardId) {
		if (stageCards.isEmpty()) {
			return List.of();
		}

		return stageCards.stream()
				.sorted(CARD_ORDER)
				.map(card -> {
					List<KanbanTagResponseDto> tags = tagsByCardId.getOrDefault(card.getId(), List.of())
							.stream()
							.map(kanbanMapper::toTagResponse)
							.toList();
					List<KanbanChecklistItemResponseDto> checklistItems = kanbanChecklistItemRepository
							.findByCardIdOrderByItemOrderAscTitleAsc(card.getId())
							.stream()
							.map(kanbanMapper::toChecklistItemResponse)
							.toList();
					return kanbanMapper.toCardResponse(card, tags, checklistItems);
				})
				.toList();
	}

	private KanbanCardResponseDto toCardResponse(KanbanCard card) {
		Map<UUID, List<KanbanTag>> tagsByCardId = loadTagsByCardIds(List.of(card.getId()));
		List<KanbanTagResponseDto> tags = tagsByCardId.getOrDefault(card.getId(), List.of())
				.stream()
				.map(kanbanMapper::toTagResponse)
				.toList();
		List<KanbanChecklistItemResponseDto> checklistItems = kanbanChecklistItemRepository
				.findByCardIdOrderByItemOrderAscTitleAsc(card.getId())
				.stream()
				.map(kanbanMapper::toChecklistItemResponse)
				.toList();
		return kanbanMapper.toCardResponse(card, tags, checklistItems);
	}

	private void replaceCardTags(UUID trackerId, KanbanCard card, List<UUID> tagIds) {
		kanbanCardTagAssignmentRepository.deleteByCardId(card.getId());
		if (tagIds == null || tagIds.isEmpty()) {
			return;
		}

		List<KanbanTag> tags = resolveExistingTags(trackerId, tagIds);
		List<KanbanCardTagAssignment> assignments = tags.stream()
				.map(tag -> KanbanCardTagAssignment.builder().card(card).tag(tag).build())
				.toList();
		kanbanCardTagAssignmentRepository.saveAll(assignments);
	}

	private List<KanbanTag> resolveExistingTags(UUID trackerId, List<UUID> tagIds) {
		Set<UUID> uniqueTagIds = tagIds.stream()
				.filter(java.util.Objects::nonNull)
				.collect(Collectors.toCollection(HashSet::new));

		if (uniqueTagIds.isEmpty()) {
			throw new InvalidKanbanException("tagIds must contain at least one valid id");
		}

		List<KanbanTag> tags = kanbanTagRepository.findByExpenseTrackerIdAndIdInAndIsDeletedFalse(trackerId, uniqueTagIds);
		if (tags.size() != uniqueTagIds.size()) {
			throw new KanbanTagNotFoundException("Some kanban tags were not found on this expense tracker");
		}
		return tags;
	}

	private Map<UUID, List<KanbanTag>> loadTagsByCardIds(List<UUID> cardIds) {
		if (cardIds.isEmpty()) {
			return Map.of();
		}

		return kanbanCardTagAssignmentRepository.findAllByCardIdsWithTag(cardIds)
				.stream()
				.collect(Collectors.groupingBy(
						a -> a.getCard().getId(),
						Collectors.mapping(KanbanCardTagAssignment::getTag, Collectors.toList())
				));
	}

	private KanbanStage resolveStage(UUID boardId, UUID stageId) {
		if (stageId == null) {
			List<KanbanStage> stages = kanbanStageRepository.findByBoardIdAndIsDeletedFalseOrderByStageOrderAscNameAsc(boardId);
			if (stages.isEmpty()) {
				throw new InvalidKanbanException("Kanban board must have at least one stage before creating cards");
			}
			return stages.getFirst();
		}
		KanbanStage stage = getStageOrThrow(stageId);
		if (!stage.getBoard().getId().equals(boardId)) {
			throw new KanbanStageNotFoundException("Kanban stage not found on this board");
		}
		return stage;
	}

	private void assertUniqueTitle(UUID boardId, String title, UUID currentCardId) {
		String normalized = normalizeTitle(title);
		boolean exists = currentCardId == null
				? kanbanCardRepository.existsByStageBoardIdAndTitleIgnoreCase(boardId, normalized)
				: kanbanCardRepository.existsByStageBoardIdAndTitleIgnoreCaseAndIdNot(boardId, normalized, currentCardId);

		if (exists) {
			throw new DuplicateKanbanNameException("Card with title '%s' already exists on this board".formatted(normalized));
		}
	}

	private KanbanBoard getBoardOrThrow(UUID boardId) {
		return kanbanBoardRepository.findByIdAndIsDeletedFalse(boardId)
				.orElseThrow(() -> new KanbanBoardNotFoundException("Kanban board not found with id: " + boardId));
	}

	private KanbanCard getCardOrThrow(UUID cardId) {
		return kanbanCardRepository.findById(cardId)
				.orElseThrow(() -> new KanbanCardNotFoundException("Kanban card not found with id: " + cardId));
	}

	private KanbanStage getStageOrThrow(UUID stageId) {
		return kanbanStageRepository.findByIdAndIsDeletedFalse(stageId)
				.orElseThrow(() -> new KanbanStageNotFoundException("Kanban stage not found with id: " + stageId));
	}

	private KanbanChecklistItem getChecklistItemOrThrow(UUID cardId, UUID itemId) {
		return kanbanChecklistItemRepository.findByCardIdAndId(cardId, itemId)
				.orElseThrow(() -> new InvalidKanbanException("Checklist item not found on this card"));
	}

	private void assertBoardBelongsToTracker(KanbanBoard board, UUID trackerId) {
		if (!board.getExpenseTracker().getId().equals(trackerId)) {
			throw new KanbanBoardNotFoundException("Kanban board does not belong to this expense tracker");
		}
	}

	private void assertCardBelongsToBoard(KanbanCard card, UUID boardId, UUID trackerId) {
		if (!card.getStage().getBoard().getId().equals(boardId)
				|| !card.getStage().getBoard().getExpenseTracker().getId().equals(trackerId)) {
			throw new KanbanCardNotFoundException("Kanban card not found on this board");
		}
	}

	private void assertStageBelongsToTracker(KanbanStage stage, UUID trackerId) {
		if (!stage.getBoard().getExpenseTracker().getId().equals(trackerId)) {
			throw new KanbanStageNotFoundException("Kanban stage not found on this expense tracker");
		}
	}

	private void assertHasAccessToTracker(User currentUser, UUID trackerId) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);
	}

	private String normalizeTitle(String title) {
		if (title == null || title.isBlank()) {
			throw new InvalidKanbanException("Card title is required");
		}
		return title.trim();
	}

	private String normalizeNullable(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private String normalizeImageUrl(String imageUrl) {
		String normalized = normalizeNullable(imageUrl);
		if (normalized == null) {
			return null;
		}
		if (!normalized.startsWith("https://res.cloudinary.com/") && !normalized.startsWith("http://res.cloudinary.com/")) {
			throw new InvalidKanbanException("imageUrl must be a valid Cloudinary URL");
		}
		return normalized;
	}

	private Integer validatePriority(Integer priority) {
		int value = priority == null ? 0 : priority;
		if (value < 0 || value > 10) {
			throw new InvalidKanbanException("Priority must be in range 0..10");
		}
		return value;
	}

	private Integer defaultIfNull(Integer value) {
		return value == null ? 0 : value;
	}

	private String normalizeChecklistTitle(String title) {
		if (title == null || title.isBlank()) {
			throw new InvalidKanbanException("Checklist item title is required");
		}
		return title.trim();
	}
}