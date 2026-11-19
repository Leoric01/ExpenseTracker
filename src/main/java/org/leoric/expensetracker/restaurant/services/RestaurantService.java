package org.leoric.expensetracker.restaurant.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.asset.models.constants.AssetType;
import org.leoric.expensetracker.asset.repositories.AssetRepository;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.leoric.expensetracker.image.services.interfaces.ImageService;
import org.leoric.expensetracker.restaurant.dtos.RestaurantDtos.*;
import org.leoric.expensetracker.restaurant.models.*;
import org.leoric.expensetracker.restaurant.repositories.*;
import org.leoric.expensetracker.restaurant.services.interfaces.RestaurantOperations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class RestaurantService implements RestaurantOperations {
	private static final List<String> BUILT_INS = List.of("Wolt", "Bolt", "Osobní návštěva");
	private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/heic", "image/gif", "image/avif");
	private final RestaurantRepository restaurants;
	private final RestaurantDishRepository dishes;
	private final RestaurantVisitRepository visits;
	private final RestaurantVisitItemRepository items;
	private final RestaurantPhotoRepository photos;
	private final RestaurantItemPhotoRepository itemPhotos;
	private final RestaurantServiceTagRepository tags;
	private final ExpenseTrackerRepository trackers;
	private final AssetRepository assets;
	private final ImageService images;
	private final RestaurantFileCleanupService cleanup;

	@Transactional(readOnly = true)
	public Page<RestaurantSummary> list(UUID trackerId, String search, Recommendation recommendation, PriceLevel level,
			String service, Integer minRating, Integer maxRating, String order, int page, int size) {
		if (page < 0 || size < 1 || size > 100) throw new OperationNotPermittedException("Invalid pagination");
		if (minRating != null && (minRating < 0 || minRating > 10) || maxRating != null && (maxRating < 0 || maxRating > 10)
				|| minRating != null && maxRating != null && minRating > maxRating) {
			throw new OperationNotPermittedException("Invalid rating filter");
		}
		String text = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
		String ordering = order == null ? "name" : order;
		if (!List.of("name", "rating", "recent").contains(ordering)) {
			throw new OperationNotPermittedException("Invalid restaurant sort order");
		}
		String serviceFilter = service == null ? "" : service.trim().toLowerCase(Locale.ROOT);
		return restaurants.search(trackerId, text, recommendation, level, serviceFilter, minRating, maxRating,
				ordering, PageRequest.of(page, size)).map(this::summary);
	}

	@Transactional(readOnly = true)
	public RestaurantDetail detail(UUID trackerId, UUID restaurantId) {
		Restaurant restaurant = restaurant(trackerId, restaurantId);
		List<Dish> dishDtos = dishes.findByRestaurantIdOrderByNameAsc(restaurantId).stream().map(this::dishDto).toList();
		List<Visit> visitDtos = visits.findByRestaurantId(restaurantId).stream()
				.sorted(Comparator.comparing(RestaurantVisit::getVisitedOn, Comparator.nullsLast(Comparator.reverseOrder()))
						.thenComparing(RestaurantVisit::getCreatedDate, Comparator.reverseOrder()))
				.map(this::visitDto).toList();
		return new RestaurantDetail(summary(restaurant), dishDtos, visitDtos);
	}

	public RestaurantDetail create(UUID trackerId, RestaurantInput input) {
		Restaurant restaurant = new Restaurant();
		restaurant.setExpenseTracker(trackers.findById(trackerId).orElseThrow(() -> new EntityNotFoundException("Tracker not found")));
		updateRestaurant(restaurant, input);
		restaurants.saveAndFlush(restaurant);
		return detail(trackerId, restaurant.getId());
	}

	public RestaurantDetail update(UUID trackerId, UUID restaurantId, RestaurantInput input) {
		Restaurant restaurant = restaurant(trackerId, restaurantId);
		updateRestaurant(restaurant, input);
		return detail(trackerId, restaurantId);
	}

	private void updateRestaurant(Restaurant restaurant, RestaurantInput input) {
		restaurant.setName(input.name().trim());
		restaurant.setNote(normalize(input.note()));
		restaurant.setPriceLevel(input.priceLevel());
		restaurant.setRecommendation(input.recommendation());
		List<String> allowed = serviceNames(restaurant.getExpenseTracker().getId());
		List<String> selected = new ArrayList<>();
		for (String name : input.services()) {
			String canonical = canonicalService(allowed, name);
			if (selected.stream().noneMatch(s -> s.equalsIgnoreCase(canonical))) selected.add(canonical);
		}
		restaurant.getServices().clear();
		restaurant.getServices().addAll(selected);
	}

	@Transactional(readOnly = true)
	public List<ServiceTag> services(UUID trackerId) {
		var result = new ArrayList<ServiceTag>();
		BUILT_INS.forEach(name -> result.add(new ServiceTag(null, name, true)));
		tags.findByExpenseTrackerIdOrderByNameAsc(trackerId).forEach(tag -> result.add(new ServiceTag(tag.getId(), tag.getName(), false)));
		return result;
	}

	public ServiceTag addService(UUID trackerId, ServiceInput input) {
		String name = input.name().trim();
		if (serviceNames(trackerId).stream().anyMatch(s -> s.equalsIgnoreCase(name))) {
			throw new OperationNotPermittedException("Service already exists");
		}
		var tag = new RestaurantServiceTag();
		tag.setExpenseTracker(trackers.findById(trackerId).orElseThrow(() -> new EntityNotFoundException("Tracker not found")));
		tag.setName(name);
		tags.saveAndFlush(tag);
		return new ServiceTag(tag.getId(), tag.getName(), false);
	}

	public void deleteService(UUID trackerId, UUID tagId) {
		var tag = tags.findByIdAndExpenseTrackerId(tagId, trackerId).orElseThrow(() -> new EntityNotFoundException("Service not found"));
		for (var restaurant : restaurants.findByExpenseTrackerId(trackerId)) {
			restaurant.getServices().removeIf(name -> name.equalsIgnoreCase(tag.getName()));
		}
		tags.delete(tag);
	}

	public Dish addDish(UUID trackerId, UUID restaurantId, DishInput input) {
		Restaurant restaurant = restaurant(trackerId, restaurantId);
		RestaurantDish dish = new RestaurantDish();
		dish.setRestaurant(restaurant);
		setDish(dish, input);
		dishes.saveAndFlush(dish);
		return dishDto(dish);
	}

	public Dish updateDish(UUID trackerId, UUID restaurantId, UUID dishId, DishInput input) {
		restaurant(trackerId, restaurantId);
		RestaurantDish dish = dish(restaurantId, dishId);
		setDish(dish, input);
		return dishDto(dish);
	}

	private void setDish(RestaurantDish dish, DishInput input) {
		dish.setName(input.name().trim());
		dish.setRecommendation(input.recommendation());
	}

	public void deleteDish(UUID trackerId, UUID restaurantId, UUID dishId) {
		restaurant(trackerId, restaurantId);
		RestaurantDish dish = dish(restaurantId, dishId);
		items.findByDishId(dishId).forEach(item -> item.setDish(null));
		items.flush();
		dishes.delete(dish);
	}

	public Visit addVisit(UUID trackerId, UUID restaurantId, VisitInput input) {
		var visit = new RestaurantVisit();
		visit.setRestaurant(restaurant(trackerId, restaurantId));
		setVisit(trackerId, visit, input);
		visits.saveAndFlush(visit);
		return visitDto(visit);
	}

	public Visit updateVisit(UUID trackerId, UUID restaurantId, UUID visitId, VisitInput input) {
		restaurant(trackerId, restaurantId);
		RestaurantVisit visit = visit(restaurantId, visitId);
		setVisit(trackerId, visit, input);
		return visitDto(visit);
	}

	private void setVisit(UUID trackerId, RestaurantVisit visit, VisitInput input) {
		String code = input.currencyCode().trim().toUpperCase(Locale.ROOT);
		var asset = assets.findByCodeIgnoreCase(code).filter(a -> a.getAssetType() == AssetType.FIAT)
				.orElseThrow(() -> new OperationNotPermittedException("Unknown currency"));
		boolean changed = visit.getCurrencyCode() != null && !visit.getCurrencyCode().equals(code);
		if (changed
				&& (visit.getTotalMinor() != null || visit.getDeliveryMinor() != null
				|| items.findByVisitId(visit.getId()).stream().anyMatch(i -> i.getPriceMinor() != null))) {
			throw new OperationNotPermittedException("Remove existing prices before changing currency");
		}
		visit.setCurrencyCode(code);
		if (visit.getId() == null || changed) visit.setCurrencyScale(asset.getScale());
		visit.setVisitedOn(input.visitedOn());
		visit.setNote(normalize(input.note()));
		visit.setRating(input.rating());
		visit.setTotalMinor(input.totalMinor());
		visit.setDeliveryMinor(input.deliveryMinor());
		String service = normalize(input.serviceName());
		if (service != null) {
			List<String> allowed = serviceNames(trackerId);
			String requestedService = service;
			if (allowed.stream().noneMatch(s -> s.equalsIgnoreCase(requestedService))
					&& service.equals(visit.getServiceName())) {
				visit.setServiceName(service);
				return;
			}
			service = canonicalService(allowed, service);
			String selected = service;
			if (visit.getRestaurant().getServices().stream().noneMatch(s -> s.equalsIgnoreCase(selected))) {
				visit.getRestaurant().getServices().add(selected);
			}
		}
		visit.setServiceName(service);
	}

	public Item addItem(UUID trackerId, UUID restaurantId, UUID visitId, ItemInput input) {
		restaurant(trackerId, restaurantId);
		if (input.dishId() == null) throw new OperationNotPermittedException("Dish is required");
		var item = new RestaurantVisitItem();
		item.setVisit(visit(restaurantId, visitId));
		setItem(item, dish(restaurantId, input.dishId()), input);
		items.saveAndFlush(item);
		return itemDto(item);
	}

	public Item updateItem(UUID trackerId, UUID restaurantId, UUID visitId, UUID itemId, ItemInput input) {
		restaurant(trackerId, restaurantId);
		visit(restaurantId, visitId);
		var item = item(visitId, itemId);
		if (input.dishId() == null) {
			if (item.getDish() != null) throw new OperationNotPermittedException("Dish is required");
			item.setPriceMinor(input.priceMinor());
			item.setRating(input.rating());
			item.setNote(normalize(input.note()));
		} else {
			setItem(item, dish(restaurantId, input.dishId()), input);
		}
		return itemDto(item);
	}

	private void setItem(RestaurantVisitItem item, RestaurantDish dish, ItemInput input) {
		if (item.getDish() == null || !item.getDish().getId().equals(dish.getId())) item.setNameSnapshot(dish.getName());
		item.setDish(dish);
		item.setPriceMinor(input.priceMinor());
		item.setRating(input.rating());
		item.setNote(normalize(input.note()));
	}

	public void deleteItem(UUID trackerId, UUID restaurantId, UUID visitId, UUID itemId) {
		restaurant(trackerId, restaurantId);
		visit(restaurantId, visitId);
		removeItem(item(visitId, itemId));
	}

	private void removeItem(RestaurantVisitItem item) {
		for (var photo : itemPhotos.findByItemIdOrderByCreatedDateAscIdAsc(item.getId())) {
			cleanup.enqueue(photo.getImageUrl());
			itemPhotos.delete(photo);
		}
		itemPhotos.flush();
		items.delete(item);
		items.flush();
	}

	public void deleteVisit(UUID trackerId, UUID restaurantId, UUID visitId) {
		restaurant(trackerId, restaurantId);
		removeVisit(visit(restaurantId, visitId));
	}

	private void removeVisit(RestaurantVisit visit) {
		items.findByVisitId(visit.getId()).forEach(this::removeItem);
		visits.delete(visit);
		visits.flush();
	}

	public void deleteRestaurant(UUID trackerId, UUID restaurantId) {
		removeRestaurant(restaurant(trackerId, restaurantId));
	}

	private void removeRestaurant(Restaurant restaurant) {
		visits.findByRestaurantId(restaurant.getId()).forEach(this::removeVisit);
		dishes.deleteAll(dishes.findByRestaurantIdOrderByNameAsc(restaurant.getId()));
		for (var photo : photos.findByRestaurantIdOrderByCreatedDateAscIdAsc(restaurant.getId())) {
			cleanup.enqueue(photo.getImageUrl());
			photos.delete(photo);
		}
		photos.flush();
		dishes.flush();
		restaurants.delete(restaurant);
		restaurants.flush();
	}

	public void deleteAllForTracker(UUID trackerId) {
		restaurants.findByExpenseTrackerId(trackerId).forEach(this::removeRestaurant);
		tags.deleteAll(tags.findByExpenseTrackerIdOrderByNameAsc(trackerId));
		tags.flush();
	}

	public Photo addPhoto(UUID trackerId, UUID restaurantId, MultipartFile file) {
		var restaurant = restaurant(trackerId, restaurantId);
		String url = upload(file);
		var photo = new RestaurantPhoto();
		photo.setRestaurant(restaurant);
		photo.setImageUrl(url);
		photos.saveAndFlush(photo);
		return new Photo(photo.getId(), url);
	}

	public Photo addItemPhoto(UUID trackerId, UUID restaurantId, UUID visitId, UUID itemId, MultipartFile file) {
		restaurant(trackerId, restaurantId);
		visit(restaurantId, visitId);
		var item = item(visitId, itemId);
		String url = upload(file);
		var photo = new RestaurantItemPhoto();
		photo.setItem(item);
		photo.setImageUrl(url);
		itemPhotos.saveAndFlush(photo);
		return new Photo(photo.getId(), url);
	}

	private String upload(MultipartFile file) {
		if (file == null || file.isEmpty() || !IMAGE_TYPES.contains(file.getContentType())) {
			throw new OperationNotPermittedException("A supported image is required");
		}
		String url = images.uploadImage(file, "expense-tracker/restaurants");
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override public void afterCompletion(int status) {
				if (status != STATUS_COMMITTED) {
					try {
						cleanup.enqueueAfterRollback(url);
						cleanup.process();
					} catch (RuntimeException e) {
						org.slf4j.LoggerFactory.getLogger(RestaurantService.class)
								.error("Failed to queue cleanup for an uncommitted restaurant image {}", url, e);
					}
				}
			}
		});
		return url;
	}

	public void deletePhoto(UUID trackerId, UUID restaurantId, UUID photoId) {
		restaurant(trackerId, restaurantId);
		var photo = photos.findByIdAndRestaurantId(photoId, restaurantId).orElseThrow(() -> new EntityNotFoundException("Photo not found"));
		cleanup.enqueue(photo.getImageUrl());
		photos.delete(photo);
	}

	public void deleteItemPhoto(UUID trackerId, UUID restaurantId, UUID visitId, UUID itemId, UUID photoId) {
		restaurant(trackerId, restaurantId);
		visit(restaurantId, visitId);
		item(visitId, itemId);
		var photo = itemPhotos.findByIdAndItemId(photoId, itemId).orElseThrow(() -> new EntityNotFoundException("Photo not found"));
		cleanup.enqueue(photo.getImageUrl());
		itemPhotos.delete(photo);
	}

	private RestaurantSummary summary(Restaurant restaurant) {
		var all = visits.findByRestaurantId(restaurant.getId());
		var ratings = all.stream().map(RestaurantVisit::getRating).filter(Objects::nonNull).toList();
		Double average = ratings.stream().mapToInt(Integer::intValue).average().isPresent()
				? ratings.stream().mapToInt(Integer::intValue).average().orElseThrow() : null;
		Integer worst = ratings.stream().min(Integer::compareTo).orElse(null);
		var last = all.stream().map(RestaurantVisit::getVisitedOn).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
		return new RestaurantSummary(restaurant.getId(), restaurant.getName(), restaurant.getNote(), restaurant.getPriceLevel(),
				restaurant.getRecommendation(), List.copyOf(restaurant.getServices()), average, worst, ratings.size(), last,
				photos.findByRestaurantIdOrderByCreatedDateAscIdAsc(restaurant.getId()).stream().map(p -> new Photo(p.getId(), p.getImageUrl())).toList());
	}

	private Dish dishDto(RestaurantDish dish) {
		var ratings = items.findByDishId(dish.getId()).stream().map(RestaurantVisitItem::getRating).filter(Objects::nonNull).toList();
		var average = ratings.stream().mapToInt(Integer::intValue).average();
		return new Dish(dish.getId(), dish.getName(), dish.getRecommendation(), average.isPresent() ? average.getAsDouble() : null,
				ratings.stream().min(Integer::compareTo).orElse(null), ratings.size());
	}

	private Visit visitDto(RestaurantVisit visit) {
		return new Visit(visit.getId(), visit.getVisitedOn(), visit.getServiceName(), visit.getNote(), visit.getRating(),
				visit.getCurrencyCode(), visit.getCurrencyScale(), visit.getTotalMinor(), visit.getDeliveryMinor(),
				visit.getCreatedDate(), items.findByVisitId(visit.getId()).stream().map(this::itemDto).toList());
	}

	private Item itemDto(RestaurantVisitItem item) {
		var dish = item.getDish();
		return new Item(item.getId(), dish == null ? null : dish.getId(), item.getNameSnapshot(),
				dish == null ? null : dish.getRecommendation(), item.getPriceMinor(), item.getRating(), item.getNote(),
				itemPhotos.findByItemIdOrderByCreatedDateAscIdAsc(item.getId()).stream().map(p -> new Photo(p.getId(), p.getImageUrl())).toList());
	}

	private Restaurant restaurant(UUID trackerId, UUID id) {
		return restaurants.findByIdAndExpenseTrackerId(id, trackerId).orElseThrow(() -> new EntityNotFoundException("Restaurant not found"));
	}
	private RestaurantDish dish(UUID restaurantId, UUID id) {
		return dishes.findByIdAndRestaurantId(id, restaurantId).orElseThrow(() -> new EntityNotFoundException("Dish not found"));
	}
	private RestaurantVisit visit(UUID restaurantId, UUID id) {
		return visits.findByIdAndRestaurantId(id, restaurantId).orElseThrow(() -> new EntityNotFoundException("Visit not found"));
	}
	private RestaurantVisitItem item(UUID visitId, UUID id) {
		return items.findByIdAndVisitId(id, visitId).orElseThrow(() -> new EntityNotFoundException("Item not found"));
	}
	private List<String> serviceNames(UUID trackerId) { return services(trackerId).stream().map(ServiceTag::name).toList(); }
	private String canonicalService(List<String> allowed, String input) {
		return allowed.stream().filter(s -> s.equalsIgnoreCase(input.trim())).findFirst()
				.orElseThrow(() -> new OperationNotPermittedException("Unknown service"));
	}
	private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
