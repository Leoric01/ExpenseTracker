package org.leoric.expensetracker.auth.services.interfaces;

import org.leoric.expensetracker.auth.dto.WidgetItemResponseDto;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.auth.models.constants.WidgetType;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public interface WidgetItemService {

	List<WidgetItemResponseDto> widgetItemFindAll(User currentUser, WidgetType widgetType);

	List<WidgetItemResponseDto> widgetItemReplace(User currentUser, WidgetType widgetType, List<UUID> entityIds);

	void widgetItemAdd(User currentUser, WidgetType widgetType, UUID entityId);

	void widgetItemRemove(User currentUser, WidgetType widgetType, UUID entityId);
}