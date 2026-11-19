package org.leoric.expensetracker.restaurant.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessService;
import org.leoric.expensetracker.restaurant.dtos.RestaurantDtos.*;
import org.leoric.expensetracker.restaurant.models.PriceLevel;
import org.leoric.expensetracker.restaurant.models.Recommendation;
import org.leoric.expensetracker.restaurant.services.RestaurantFileCleanupService;
import org.leoric.expensetracker.restaurant.services.interfaces.RestaurantOperations;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_MEMBER;
import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_OWNER;

@RestController
@RequestMapping("/api/restaurants/{trackerId}")
@RequiredArgsConstructor
public class RestaurantController {
	private final RestaurantOperations service;
	private final RestaurantFileCleanupService cleanup;
	private final ExpenseTrackerAccessService access;

	private void authorize(UUID trackerId, User user) {
		access.assertHasRoleOnExpenseTracker(trackerId, user, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
	}

	@GetMapping
	public Page<RestaurantSummary> list(@AuthenticationPrincipal User user, @PathVariable UUID trackerId,
			@RequestParam(required = false) String search, @RequestParam(required = false) Recommendation recommendation,
			@RequestParam(required = false) PriceLevel priceLevel, @RequestParam(required = false) String serviceName,
			@RequestParam(required = false) Integer minRating, @RequestParam(required = false) Integer maxRating,
			@RequestParam(defaultValue = "name") String order, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		authorize(trackerId, user);
		return service.list(trackerId, search, recommendation, priceLevel, serviceName, minRating, maxRating, order, page, size);
	}

	@GetMapping("/services")
	public List<ServiceTag> services(@AuthenticationPrincipal User user, @PathVariable UUID trackerId) {
		authorize(trackerId, user);
		return service.services(trackerId);
	}
	@PostMapping("/services")
	public ResponseEntity<ServiceTag> createService(@AuthenticationPrincipal User user, @PathVariable UUID trackerId,
			@Valid @RequestBody ServiceInput input) {
		authorize(trackerId, user);
		return ResponseEntity.status(201).body(service.addService(trackerId, input));
	}
	@DeleteMapping("/services/{tagId}")
	public ResponseEntity<Void> deleteService(@AuthenticationPrincipal User user, @PathVariable UUID trackerId, @PathVariable UUID tagId) {
		authorize(trackerId, user);
		service.deleteService(trackerId, tagId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping
	public ResponseEntity<RestaurantDetail> create(@AuthenticationPrincipal User user, @PathVariable UUID trackerId,
			@Valid @RequestBody RestaurantInput input) {
		authorize(trackerId, user);
		return ResponseEntity.status(201).body(service.create(trackerId, input));
	}
	@GetMapping("/{restaurantId}")
	public RestaurantDetail detail(@AuthenticationPrincipal User user, @PathVariable UUID trackerId, @PathVariable UUID restaurantId) {
		authorize(trackerId, user);
		return service.detail(trackerId, restaurantId);
	}
	@PutMapping("/{restaurantId}")
	public RestaurantDetail update(@AuthenticationPrincipal User user, @PathVariable UUID trackerId, @PathVariable UUID restaurantId,
			@Valid @RequestBody RestaurantInput input) {
		authorize(trackerId, user);
		return service.update(trackerId, restaurantId, input);
	}
	@DeleteMapping("/{restaurantId}")
	public DeleteResult delete(@AuthenticationPrincipal User user, @PathVariable UUID trackerId, @PathVariable UUID restaurantId) {
		authorize(trackerId, user);
		service.deleteRestaurant(trackerId, restaurantId);
		return new DeleteResult(cleanup.process());
	}

	@PostMapping("/{restaurantId}/dishes")
	public ResponseEntity<Dish> createDish(@AuthenticationPrincipal User user, @PathVariable UUID trackerId, @PathVariable UUID restaurantId,
			@Valid @RequestBody DishInput input) {
		authorize(trackerId, user);
		return ResponseEntity.status(201).body(service.addDish(trackerId, restaurantId, input));
	}
	@PutMapping("/{restaurantId}/dishes/{dishId}")
	public Dish updateDish(@AuthenticationPrincipal User user, @PathVariable UUID trackerId, @PathVariable UUID restaurantId,
			@PathVariable UUID dishId, @Valid @RequestBody DishInput input) {
		authorize(trackerId, user);
		return service.updateDish(trackerId, restaurantId, dishId, input);
	}
	@DeleteMapping("/{restaurantId}/dishes/{dishId}")
	public ResponseEntity<Void> deleteDish(@AuthenticationPrincipal User user, @PathVariable UUID trackerId,
			@PathVariable UUID restaurantId, @PathVariable UUID dishId) {
		authorize(trackerId, user);
		service.deleteDish(trackerId, restaurantId, dishId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{restaurantId}/visits")
	public ResponseEntity<Visit> createVisit(@AuthenticationPrincipal User user, @PathVariable UUID trackerId, @PathVariable UUID restaurantId,
			@Valid @RequestBody VisitInput input) {
		authorize(trackerId, user);
		return ResponseEntity.status(201).body(service.addVisit(trackerId, restaurantId, input));
	}
	@PutMapping("/{restaurantId}/visits/{visitId}")
	public Visit updateVisit(@AuthenticationPrincipal User user, @PathVariable UUID trackerId, @PathVariable UUID restaurantId,
			@PathVariable UUID visitId, @Valid @RequestBody VisitInput input) {
		authorize(trackerId, user);
		return service.updateVisit(trackerId, restaurantId, visitId, input);
	}
	@DeleteMapping("/{restaurantId}/visits/{visitId}")
	public DeleteResult deleteVisit(@AuthenticationPrincipal User user, @PathVariable UUID trackerId,
			@PathVariable UUID restaurantId, @PathVariable UUID visitId) {
		authorize(trackerId, user);
		service.deleteVisit(trackerId, restaurantId, visitId);
		return new DeleteResult(cleanup.process());
	}

	@PostMapping("/{restaurantId}/visits/{visitId}/items")
	public ResponseEntity<Item> createItem(@AuthenticationPrincipal User user, @PathVariable UUID trackerId, @PathVariable UUID restaurantId,
			@PathVariable UUID visitId, @Valid @RequestBody ItemInput input) {
		authorize(trackerId, user);
		return ResponseEntity.status(201).body(service.addItem(trackerId, restaurantId, visitId, input));
	}
	@PutMapping("/{restaurantId}/visits/{visitId}/items/{itemId}")
	public Item updateItem(@AuthenticationPrincipal User user, @PathVariable UUID trackerId, @PathVariable UUID restaurantId,
			@PathVariable UUID visitId, @PathVariable UUID itemId, @Valid @RequestBody ItemInput input) {
		authorize(trackerId, user);
		return service.updateItem(trackerId, restaurantId, visitId, itemId, input);
	}
	@DeleteMapping("/{restaurantId}/visits/{visitId}/items/{itemId}")
	public DeleteResult deleteItem(@AuthenticationPrincipal User user, @PathVariable UUID trackerId, @PathVariable UUID restaurantId,
			@PathVariable UUID visitId, @PathVariable UUID itemId) {
		authorize(trackerId, user);
		service.deleteItem(trackerId, restaurantId, visitId, itemId);
		return new DeleteResult(cleanup.process());
	}

	@PostMapping(value = "/{restaurantId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<Photo> uploadPhoto(@AuthenticationPrincipal User user, @PathVariable UUID trackerId,
			@PathVariable UUID restaurantId, @RequestParam MultipartFile file) {
		authorize(trackerId, user);
		return ResponseEntity.status(201).body(service.addPhoto(trackerId, restaurantId, file));
	}
	@DeleteMapping("/{restaurantId}/photos/{photoId}")
	public DeleteResult deletePhoto(@AuthenticationPrincipal User user, @PathVariable UUID trackerId,
			@PathVariable UUID restaurantId, @PathVariable UUID photoId) {
		authorize(trackerId, user);
		service.deletePhoto(trackerId, restaurantId, photoId);
		return new DeleteResult(cleanup.process());
	}
	@PostMapping(value = "/{restaurantId}/visits/{visitId}/items/{itemId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<Photo> uploadItemPhoto(@AuthenticationPrincipal User user, @PathVariable UUID trackerId, @PathVariable UUID restaurantId,
			@PathVariable UUID visitId, @PathVariable UUID itemId, @RequestParam MultipartFile file) {
		authorize(trackerId, user);
		return ResponseEntity.status(201).body(service.addItemPhoto(trackerId, restaurantId, visitId, itemId, file));
	}
	@DeleteMapping("/{restaurantId}/visits/{visitId}/items/{itemId}/photos/{photoId}")
	public DeleteResult deleteItemPhoto(@AuthenticationPrincipal User user, @PathVariable UUID trackerId, @PathVariable UUID restaurantId,
			@PathVariable UUID visitId, @PathVariable UUID itemId, @PathVariable UUID photoId) {
		authorize(trackerId, user);
		service.deleteItemPhoto(trackerId, restaurantId, visitId, itemId, photoId);
		return new DeleteResult(cleanup.process());
	}
	@PostMapping("/cleanup")
	public DeleteResult retryCleanup(@AuthenticationPrincipal User user, @PathVariable UUID trackerId) {
		authorize(trackerId, user);
		return new DeleteResult(cleanup.process());
	}
}
