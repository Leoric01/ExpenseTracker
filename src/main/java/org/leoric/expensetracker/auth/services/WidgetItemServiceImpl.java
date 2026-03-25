package org.leoric.expensetracker.auth.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.auth.dto.WidgetItemResponseDto;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.auth.models.WidgetItem;
import org.leoric.expensetracker.auth.models.constants.WidgetType;
import org.leoric.expensetracker.auth.repositories.WidgetItemRepository;
import org.leoric.expensetracker.auth.services.interfaces.WidgetItemService;
import org.leoric.expensetracker.handler.exceptions.DuplicateWidgetItemEntityIdsException;
import org.leoric.expensetracker.handler.exceptions.WidgetItemReorderMismatchException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class WidgetItemServiceImpl implements WidgetItemService {

	private final WidgetItemRepository widgetItemRepository;

	@Override
	public List<WidgetItemResponseDto> widgetItemFindAll(User currentUser, WidgetType widgetType) {
		return widgetItemRepository.findByUserIdAndWidgetTypeOrderBySortOrder(currentUser.getId(), widgetType)
				.stream()
				.map(item -> new WidgetItemResponseDto(item.getEntityId(), item.getSortOrder()))
				.toList();
	}

	@Override
	@Transactional
	public List<WidgetItemResponseDto> widgetItemReplace(User currentUser, WidgetType widgetType, List<UUID> entityIds) {
		if (entityIds.size() != new HashSet<>(entityIds).size()) {
			throw new DuplicateWidgetItemEntityIdsException("Widget reorder contains duplicate entity ids for widget type: " + widgetType);
		}

		List<WidgetItem> existingItems = widgetItemRepository.findByUserIdAndWidgetTypeOrderBySortOrder(currentUser.getId(), widgetType);

		Map<UUID, WidgetItem> byEntityId = existingItems.stream()
				.collect(Collectors.toMap(WidgetItem::getEntityId, Function.identity()));

		if (existingItems.size() != entityIds.size() || !byEntityId.keySet().equals(new HashSet<>(entityIds))) {
			throw new WidgetItemReorderMismatchException(
					"Widget reorder payload does not match existing widget items for widget type: " + widgetType
			);
		}

		List<WidgetItem> updatedItems = new ArrayList<>();
		for (int i = 0; i < entityIds.size(); i++) {
			WidgetItem item = byEntityId.get(entityIds.get(i));
			item.setSortOrder(i);
			updatedItems.add(item);
		}

		widgetItemRepository.saveAll(updatedItems);

		return updatedItems.stream()
				.map(item -> new WidgetItemResponseDto(item.getEntityId(), item.getSortOrder()))
				.toList();
	}
	@Override
	@Transactional
	public void widgetItemAdd(User currentUser, WidgetType widgetType, UUID entityId) {
		if (widgetItemRepository.existsByUserIdAndWidgetTypeAndEntityId(currentUser.getId(), widgetType, entityId)) {
			return;
		}

		List<WidgetItem> existing = widgetItemRepository.findByUserIdAndWidgetTypeOrderBySortOrder(currentUser.getId(), widgetType);
		int nextOrder = existing.isEmpty() ? 0 : existing.getLast().getSortOrder() + 1;

		widgetItemRepository.save(WidgetItem.builder()
				                          .user(currentUser)
				                          .widgetType(widgetType)
				                          .entityId(entityId)
				                          .sortOrder(nextOrder)
				                          .build());

		log.info("User {} added {} to widget {}", currentUser.getEmail(), entityId, widgetType);
	}

	@Override
	@Transactional
	public void widgetItemRemove(User currentUser, WidgetType widgetType, UUID entityId) {
		widgetItemRepository.deleteByUserIdAndWidgetTypeAndEntityId(currentUser.getId(), widgetType, entityId);
		log.info("User {} removed {} from widget {}", currentUser.getEmail(), entityId, widgetType);
	}
}