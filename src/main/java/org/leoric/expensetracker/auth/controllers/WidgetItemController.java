package org.leoric.expensetracker.auth.controllers;

import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.dto.WidgetItemResponseDto;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.auth.models.constants.WidgetType;
import org.leoric.expensetracker.auth.services.interfaces.WidgetItemService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/widget-items")
@RequiredArgsConstructor
public class WidgetItemController {

	private final WidgetItemService widgetItemService;

	@GetMapping("/{widgetType}")
	public ResponseEntity<List<WidgetItemResponseDto>> widgetItemFindAll(
			@AuthenticationPrincipal User currentUser,
			@PathVariable WidgetType widgetType) {
		return ResponseEntity.ok(widgetItemService.widgetItemFindAll(currentUser, widgetType));
	}

	@PutMapping("/{widgetType}")
	public ResponseEntity<List<WidgetItemResponseDto>> widgetItemReplace(
			@AuthenticationPrincipal User currentUser,
			@PathVariable WidgetType widgetType,
			@RequestBody List<UUID> entityIds) {
		return ResponseEntity.ok(widgetItemService.widgetItemReplace(currentUser, widgetType, entityIds));
	}

	@PostMapping("/{widgetType}/{entityId}")
	public ResponseEntity<Void> widgetItemAdd(
			@AuthenticationPrincipal User currentUser,
			@PathVariable WidgetType widgetType,
			@PathVariable UUID entityId) {
		widgetItemService.widgetItemAdd(currentUser, widgetType, entityId);
		return ResponseEntity.ok().build();
	}

	@DeleteMapping("/{widgetType}/{entityId}")
	public ResponseEntity<Void> widgetItemRemove(
			@AuthenticationPrincipal User currentUser,
			@PathVariable WidgetType widgetType,
			@PathVariable UUID entityId) {
		widgetItemService.widgetItemRemove(currentUser, widgetType, entityId);
		return ResponseEntity.noContent().build();
	}
}