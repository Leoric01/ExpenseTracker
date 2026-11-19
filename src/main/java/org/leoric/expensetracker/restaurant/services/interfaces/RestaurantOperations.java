package org.leoric.expensetracker.restaurant.services.interfaces;

import org.leoric.expensetracker.restaurant.dtos.RestaurantDtos.*;
import org.leoric.expensetracker.restaurant.models.PriceLevel;
import org.leoric.expensetracker.restaurant.models.Recommendation;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface RestaurantOperations {
	Page<RestaurantSummary> list(UUID trackerId, String search, Recommendation recommendation, PriceLevel level,
			String service, Integer minRating, Integer maxRating, String order, int page, int size);
	RestaurantDetail detail(UUID trackerId, UUID restaurantId);
	RestaurantDetail create(UUID trackerId, RestaurantInput input);
	RestaurantDetail update(UUID trackerId, UUID restaurantId, RestaurantInput input);
	void deleteRestaurant(UUID trackerId, UUID restaurantId);
	void deleteAllForTracker(UUID trackerId);
	List<ServiceTag> services(UUID trackerId);
	ServiceTag addService(UUID trackerId, ServiceInput input);
	void deleteService(UUID trackerId, UUID tagId);
	Dish addDish(UUID trackerId, UUID restaurantId, DishInput input);
	Dish updateDish(UUID trackerId, UUID restaurantId, UUID dishId, DishInput input);
	void deleteDish(UUID trackerId, UUID restaurantId, UUID dishId);
	Visit addVisit(UUID trackerId, UUID restaurantId, VisitInput input);
	Visit updateVisit(UUID trackerId, UUID restaurantId, UUID visitId, VisitInput input);
	void deleteVisit(UUID trackerId, UUID restaurantId, UUID visitId);
	Item addItem(UUID trackerId, UUID restaurantId, UUID visitId, ItemInput input);
	Item updateItem(UUID trackerId, UUID restaurantId, UUID visitId, UUID itemId, ItemInput input);
	void deleteItem(UUID trackerId, UUID restaurantId, UUID visitId, UUID itemId);
	Photo addPhoto(UUID trackerId, UUID restaurantId, MultipartFile file);
	Photo addItemPhoto(UUID trackerId, UUID restaurantId, UUID visitId, UUID itemId, MultipartFile file);
	void deletePhoto(UUID trackerId, UUID restaurantId, UUID photoId);
	void deleteItemPhoto(UUID trackerId, UUID restaurantId, UUID visitId, UUID itemId, UUID photoId);
}
