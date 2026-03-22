package org.leoric.expensetracker.auth.repositories;

import org.leoric.expensetracker.auth.models.WidgetItem;
import org.leoric.expensetracker.auth.models.constants.WidgetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WidgetItemRepository extends JpaRepository<WidgetItem, UUID> {

	List<WidgetItem> findByUserIdAndWidgetTypeOrderBySortOrder(UUID userId, WidgetType widgetType);

	void deleteByUserIdAndWidgetType(UUID userId, WidgetType widgetType);

	void deleteByUserIdAndWidgetTypeAndEntityId(UUID userId, WidgetType widgetType, UUID entityId);

	boolean existsByUserIdAndWidgetTypeAndEntityId(UUID userId, WidgetType widgetType, UUID entityId);
}