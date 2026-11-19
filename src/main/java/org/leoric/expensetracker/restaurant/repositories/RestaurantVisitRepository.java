package org.leoric.expensetracker.restaurant.repositories;

import org.leoric.expensetracker.restaurant.models.RestaurantVisit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RestaurantVisitRepository extends JpaRepository<RestaurantVisit, UUID> {
	List<RestaurantVisit> findByRestaurantId(UUID restaurantId);
	Optional<RestaurantVisit> findByIdAndRestaurantId(UUID id, UUID restaurantId);
}
