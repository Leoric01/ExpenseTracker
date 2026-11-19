package org.leoric.expensetracker.restaurant.repositories;

import org.leoric.expensetracker.restaurant.models.RestaurantDish;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RestaurantDishRepository extends JpaRepository<RestaurantDish, UUID> {
	List<RestaurantDish> findByRestaurantIdOrderByNameAsc(UUID restaurantId);
	Optional<RestaurantDish> findByIdAndRestaurantId(UUID id, UUID restaurantId);
}
