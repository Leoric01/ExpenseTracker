package org.leoric.expensetracker.auth.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.auth.dto.WidgetItemResponseDto;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.auth.models.WidgetItem;
import org.leoric.expensetracker.auth.models.constants.WidgetType;
import org.leoric.expensetracker.auth.repositories.WidgetItemRepository;
import org.leoric.expensetracker.auth.services.interfaces.WidgetItemService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
		widgetItemRepository.deleteByUserIdAndWidgetType(currentUser.getId(), widgetType);

		List<WidgetItem> items = new ArrayList<>();
		for (int i = 0; i < entityIds.size(); i++) {
			items.add(WidgetItem.builder()
					.user(currentUser)
					.widgetType(widgetType)
					.entityId(entityIds.get(i))
					.sortOrder(i)
					.build());
		}

		widgetItemRepository.saveAll(items);
		log.info("User {} replaced widget items for {}: {} items", currentUser.getEmail(), widgetType, items.size());

		return items.stream()
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