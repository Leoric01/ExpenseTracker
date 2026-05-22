package org.leoric.expensetracker.kanban.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.handler.exceptions.DuplicateKanbanNameException;
import org.leoric.expensetracker.handler.exceptions.InvalidKanbanException;
import org.leoric.expensetracker.image.services.interfaces.ImageService;
import org.leoric.expensetracker.handler.exceptions.KanbanBoardNotFoundException;
import org.leoric.expensetracker.handler.exceptions.KanbanCardNotFoundException;
import org.leoric.expensetracker.handler.exceptions.KanbanStageNotFoundException;
import org.leoric.expensetracker.handler.exceptions.KanbanTagNotFoundException;
import org.leoric.expensetracker.kanban.dtos.KanbanBoardSnapshotResponseDto;
import org.leoric.expensetracker.kanban.dtos.KanbanCardMoveRequestDto;
import org.leoric.expensetracker.kanban.dtos.KanbanCardResponseDto;
import org.leoric.expensetracker.kanban.dtos.KanbanCardUpsertRequestDto;
import org.leoric.expensetracker.kanban.dtos.KanbanStageCardsResponseDto;
import org.leoric.expensetracker.kanban.dtos.KanbanTagResponseDto;
import org.leoric.expensetracker.kanban.mapstruct.KanbanMapper;
import org.leoric.expensetracker.kanban.models.KanbanBoard;
import org.leoric.expensetracker.kanban.models.KanbanCard;
import org.leoric.expensetracker.kanban.models.KanbanCardStageHistory;
import org.leoric.expensetracker.kanban.models.KanbanCardTagAssignment;
import org.leoric.expensetracker.kanban.models.KanbanStage;
import org.leoric.expensetracker.kanban.models.KanbanTag;
import org.leoric.expensetracker.kanban.repositories.KanbanBoardRepository;
import org.leoric.expensetracker.kanban.repositories.KanbanCardRepository;
import org.leoric.expensetracker.kanban.repositories.KanbanCardTagAssignmentRepository;
import org.leoric.expensetracker.kanban.repositories.KanbanStageRepository;
import org.leoric.expensetracker.kanban.repositories.KanbanTagRepository;
import org.leoric.expensetracker.kanban.services.interfaces.KanbanCardService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class KanbanCardServiceImpl implements KanbanCardService {

	private static final int MAX_SUBTASK_DEPTH = 3;
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
	private final KanbanCardTagAssignmentRepository kanbanCardTagAssignmentRepository;
	private final ImageService imageService;
	private final KanbanMapper kanbanMapper;

	// PRESUN KARTY MEZI STAGE MUSI BYT JEDNA TRANSAKCE
	@Override
	@Transactional
	public KanbanCardResponseDto kanbanCardMove(User currentUser, UUID trackerId, UUID cardId, UUID targetStageId) {
		KanbanCard card = getCardOrThrow(cardId);
		KanbanStage fromStage = card.getStage();
		KanbanStage toStage = getStageOrThrow(targetStageId);

		assertStageBelongsToTracker(toStage, trackerId);
		assertStageBelongsToTracker(fromStage, trackerId);
		assertHasAccessToTracker(currentUser, trackerId);

		card.setStage(toStage);

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

		return mapper.toResponse(card);
	}

	@Override
	@Transactional
	public KanbanCardResponseDto kanbanCardCreate(User currentUser, UUID trackerId, UUID boardId, KanbanCardUpsertRequestDto request) {
		KanbanBoard board = getBoardOrThrow(boardId);
		assertBoardBelongsToTracker(board, trackerId);
		KanbanStage stage = resolveStage(boardId, request.stageId());
		KanbanCard parent = resolveParent(boardId, request.parentCardId(), null);
		assertUniqueTitle(boardId, parent, request.title(), null);

		KanbanCard card = KanbanCard.builder()
				.stage(stage)
				.parent(parent)
				.title(normalizeTitle(request.title()))
				.description(normalizeNullable(request.description()))
				.imageUrl(normalizeImageUrl(request.imageUrl()))
				.priority(validatePriority(request.priority()))
				.dueDate(request.dueDate())
				.cardOrder(defaultIfNull(request.cardOrder()))
				.active(request.active() == null || request.active())
				.build();

		card = kanbanCardRepository.save(card);
		replaceCardTags(boardId, card, request.tagIds());
		return toCardTreeResponse(card);
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
		List<KanbanCard> allCards = kanbanCardRepository.findByBoardIdAndIsDeletedFalse(boardId);
		Map<UUID, List<KanbanCard>> cardsByStage = allCards.stream().collect(Collectors.groupingBy(c -> c.getStage().getId()));
		Map<UUID, List<KanbanTag>> tagsByCardId = loadTagsByCardIds(allCards.stream().map(KanbanCard::getId).toList());

		List<KanbanStageCardsResponseDto> stageDtos = stages.stream()
				.map(stage -> new KanbanStageCardsResponseDto(
						stage.getId(),
						stage.getName(),
						stage.getStageOrder(),
						stage.isVisibleOnBoard(),
						buildStageTree(cardsByStage.getOrDefault(stage.getId(), List.of()), tagsByCardId)
				))
				.toList();

		return new KanbanBoardSnapshotResponseDto(board.getId(), board.getName(), stageDtos);
	}

	@Override
	@Transactional(readOnly = true)
	public KanbanCardResponseDto kanbanCardFindById(User currentUser, UUID trackerId, UUID boardId, UUID cardId) {
		KanbanCard card = getCardOrThrow(cardId);
		assertCardBelongsToBoard(card, boardId, trackerId);
		return toCardTreeResponse(card);
	}

	@Override
	@Transactional
	public KanbanCardResponseDto kanbanCardUpdate(User currentUser, UUID trackerId, UUID boardId, UUID cardId, KanbanCardUpsertRequestDto request) {
		KanbanCard card = getCardOrThrow(cardId);
		assertCardBelongsToBoard(card, boardId, trackerId);
		KanbanStage stage = resolveStage(boardId, request.stageId());
		KanbanCard parent = resolveParent(boardId, request.parentCardId(), cardId);
		assertMoveDepthAllowed(parent, card);
		assertUniqueTitle(boardId, parent, request.title(), cardId);

		card.setStage(stage);
		card.setParent(parent);
		card.setTitle(normalizeTitle(request.title()));
		card.setDescription(normalizeNullable(request.description()));
		card.setImageUrl(normalizeImageUrl(request.imageUrl()));
		card.setPriority(validatePriority(request.priority()));
		card.setDueDate(request.dueDate());
		card.setCardOrder(defaultIfNull(request.cardOrder()));
		if (request.active() != null) {
			card.setActive(request.active());
		}

		card = kanbanCardRepository.save(card);
		replaceCardTags(boardId, card, request.tagIds());
		return toCardTreeResponse(card);
	}

	@Override
	@Transactional
	public KanbanCardResponseDto kanbanCardMove(User currentUser, UUID trackerId, UUID boardId, UUID cardId, KanbanCardMoveRequestDto request) {
		KanbanCard card = getCardOrThrow(cardId);
		assertCardBelongsToBoard(card, boardId, trackerId);

		KanbanStage stage = request.stageId() != null ? resolveStage(boardId, request.stageId()) : card.getStage();
		KanbanCard parent = resolveParent(boardId, request.parentCardId(), cardId);
		assertMoveDepthAllowed(parent, card);

		card.setStage(stage);
		card.setParent(parent);
		card.setCardOrder(defaultIfNull(request.cardOrder()));

		List<KanbanCard> subtree = collectSubtree(card);
		for (KanbanCard node : subtree) {
			node.setStage(stage);
		}
		kanbanCardRepository.saveAll(subtree);
		return toCardTreeResponse(card);
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
		return toCardTreeResponse(card);
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
		List<KanbanCard> subtree = collectSubtree(card);
		for (KanbanCard node : subtree) {
			node.setDeleted(true);
			node.setActive(false);
		}
		kanbanCardRepository.saveAll(subtree);
	}

	private List<KanbanCardResponseDto> buildStageTree(List<KanbanCard> stageCards, Map<UUID, List<KanbanTag>> tagsByCardId) {
		if (stageCards.isEmpty()) {
			return List.of();
		}

		Map<UUID, KanbanCard> byId = stageCards.stream().collect(Collectors.toMap(KanbanCard::getId, Function.identity()));
		Map<UUID, List<KanbanCard>> childrenByParentId = new HashMap<>();
		List<KanbanCard> roots = new ArrayList<>();

		for (KanbanCard card : stageCards) {
			KanbanCard parent = card.getParent();
			if (parent == null || !byId.containsKey(parent.getId())) {
				roots.add(card);
			} else {
				childrenByParentId.computeIfAbsent(parent.getId(), ignored -> new ArrayList<>()).add(card);
			}
		}

		roots.sort(CARD_ORDER);
		childrenByParentId.values().forEach(list -> list.sort(CARD_ORDER));
		return roots.stream().map(root -> toDtoRecursive(root, childrenByParentId, tagsByCardId)).toList();
	}

	private KanbanCardResponseDto toCardTreeResponse(KanbanCard root) {
		List<KanbanCard> subtree = collectSubtree(root);
		Map<UUID, List<KanbanTag>> tagsByCardId = loadTagsByCardIds(subtree.stream().map(KanbanCard::getId).toList());
		Map<UUID, List<KanbanCard>> childrenByParentId = subtree.stream()
				.filter(c -> c.getParent() != null)
				.collect(Collectors.groupingBy(c -> c.getParent().getId()));
		childrenByParentId.values().forEach(list -> list.sort(CARD_ORDER));
		return toDtoRecursive(root, childrenByParentId, tagsByCardId);
	}

	private KanbanCardResponseDto toDtoRecursive(
			KanbanCard card,
			Map<UUID, List<KanbanCard>> childrenByParentId,
			Map<UUID, List<KanbanTag>> tagsByCardId
	) {
		List<KanbanCardResponseDto> children = childrenByParentId.getOrDefault(card.getId(), List.of())
				.stream()
				.map(child -> toDtoRecursive(child, childrenByParentId, tagsByCardId))
				.toList();

		List<KanbanTagResponseDto> tags = tagsByCardId.getOrDefault(card.getId(), List.of())
				.stream()
				.map(kanbanMapper::toTagResponse)
				.toList();

		return kanbanMapper.toCardResponse(card, tags, children);
	}

	private void replaceCardTags(UUID boardId, KanbanCard card, List<UUID> tagIds) {
		kanbanCardTagAssignmentRepository.deleteByCardId(card.getId());
		if (tagIds == null || tagIds.isEmpty()) {
			return;
		}

		List<KanbanTag> tags = resolveExistingTags(boardId, tagIds);
		List<KanbanCardTagAssignment> assignments = tags.stream()
				.map(tag -> KanbanCardTagAssignment.builder().card(card).tag(tag).build())
				.toList();
		kanbanCardTagAssignmentRepository.saveAll(assignments);
	}

	private List<KanbanTag> resolveExistingTags(UUID boardId, List<UUID> tagIds) {
		Set<UUID> uniqueTagIds = tagIds.stream()
				.filter(java.util.Objects::nonNull)
				.collect(Collectors.toCollection(HashSet::new));

		if (uniqueTagIds.isEmpty()) {
			throw new InvalidKanbanException("tagIds must contain at least one valid id");
		}

		List<KanbanTag> tags = kanbanTagRepository.findByBoardIdAndIdInAndIsDeletedFalse(boardId, uniqueTagIds);
		if (tags.size() != uniqueTagIds.size()) {
			throw new KanbanTagNotFoundException("Some kanban tags were not found on this board");
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
		KanbanStage stage = kanbanStageRepository.findByIdAndIsDeletedFalse(stageId)
				.orElseThrow(() -> new KanbanStageNotFoundException("Kanban stage not found with id: " + stageId));
		if (!stage.getBoard().getId().equals(boardId)) {
			throw new KanbanStageNotFoundException("Kanban stage not found on this board");
		}
		return stage;
	}

	private KanbanCard resolveParent(UUID boardId, UUID parentCardId, UUID currentCardId) {
		if (parentCardId == null) {
			return null;
		}
		KanbanCard parent = getCardOrThrow(parentCardId);
		if (!parent.getBoard().getId().equals(boardId)) {
			throw new KanbanCardNotFoundException("Parent card not found on this board");
		}

		if (currentCardId != null) {
			if (parent.getId().equals(currentCardId)) {
				throw new InvalidKanbanException("Card cannot be a parent of itself");
			}
			KanbanCard cursor = parent;
			while (cursor != null) {
				if (cursor.getId().equals(currentCardId)) {
					throw new InvalidKanbanException("Invalid card move. Circular relationship detected");
				}
				cursor = cursor.getParent();
			}
		}

		int level = computeLevel(parent) + 1;
		if (level > MAX_SUBTASK_DEPTH) {
			throw new InvalidKanbanException("Card subtask depth limit exceeded. Maximum depth is %d".formatted(MAX_SUBTASK_DEPTH));
		}
		return parent;
	}

	private void assertMoveDepthAllowed(KanbanCard newParent, KanbanCard movingRoot) {
		int parentLevel = newParent == null ? 0 : computeLevel(newParent);
		int subtreeHeight = computeSubtreeHeight(movingRoot);
		if (parentLevel + subtreeHeight > MAX_SUBTASK_DEPTH) {
			throw new InvalidKanbanException("Card subtask depth limit exceeded. Maximum depth is %d".formatted(MAX_SUBTASK_DEPTH));
		}
	}

	private int computeLevel(KanbanCard card) {
		int level = 1;
		KanbanCard cursor = card;
		while (cursor.getParent() != null) {
			level++;
			cursor = cursor.getParent();
		}
		return level;
	}

	private int computeSubtreeHeight(KanbanCard root) {
		int height = 1;
		List<UUID> current = List.of(root.getId());
		int level = 1;
		while (!current.isEmpty()) {
			List<KanbanCard> children = kanbanCardRepository.findByBoardIdAndParentIdInAndIsDeletedFalse(root.getBoard().getId(), current);
			if (children.isEmpty()) {
				break;
			}
			level++;
			height = Math.max(height, level);
			current = children.stream().map(KanbanCard::getId).toList();
		}
		return height;
	}

	private void assertUniqueTitle(UUID boardId, KanbanCard parent, String title, UUID currentCardId) {
		String normalized = normalizeTitle(title);
		boolean exists;
		if (parent == null) {
			exists = currentCardId == null
					? kanbanCardRepository.existsByBoardIdAndParentIsNullAndTitleIgnoreCaseAndIsDeletedFalse(boardId, normalized)
					: kanbanCardRepository.existsByBoardIdAndParentIsNullAndTitleIgnoreCaseAndIsDeletedFalseAndIdNot(boardId, normalized, currentCardId);
		} else {
			exists = currentCardId == null
					? kanbanCardRepository.existsByBoardIdAndParentIdAndTitleIgnoreCaseAndIsDeletedFalse(boardId, parent.getId(), normalized)
					: kanbanCardRepository.existsByBoardIdAndParentIdAndTitleIgnoreCaseAndIsDeletedFalseAndIdNot(boardId, parent.getId(), normalized, currentCardId);
		}

		if (exists) {
			throw new DuplicateKanbanNameException("Card with title '%s' already exists under this parent".formatted(normalized));
		}
	}

	private List<KanbanCard> collectSubtree(KanbanCard root) {
		List<KanbanCard> result = new ArrayList<>();
		List<KanbanCard> queue = new ArrayList<>();
		queue.add(root);

		for (int i = 0; i < queue.size(); i++) {
			KanbanCard current = queue.get(i);
			result.add(current);
			List<KanbanCard> children = kanbanCardRepository.findByBoardIdAndParentIdInAndIsDeletedFalse(current.getBoard().getId(), List.of(current.getId()));
			children.stream().sorted(CARD_ORDER).forEach(queue::add);
		}
		return result;
	}

	private KanbanBoard getBoardOrThrow(UUID boardId) {
		return kanbanBoardRepository.findByIdAndIsDeletedFalse(boardId)
				.orElseThrow(() -> new KanbanBoardNotFoundException("Kanban board not found with id: " + boardId));
	}

	private KanbanCard getCardOrThrow(UUID cardId) {
		return kanbanCardRepository.findByIdAndIsDeletedFalse(cardId)
				.orElseThrow(() -> new KanbanCardNotFoundException("Kanban card not found with id: " + cardId));
	}

	private void assertBoardBelongsToTracker(KanbanBoard board, UUID trackerId) {
		if (!board.getExpenseTracker().getId().equals(trackerId)) {
			throw new KanbanBoardNotFoundException("Kanban board does not belong to this expense tracker");
		}
	}

	private void assertCardBelongsToBoard(KanbanCard card, UUID boardId, UUID trackerId) {
		if (!card.getBoard().getId().equals(boardId) || !card.getBoard().getExpenseTracker().getId().equals(trackerId)) {
			throw new KanbanCardNotFoundException("Kanban card not found on this board");
		}
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
}