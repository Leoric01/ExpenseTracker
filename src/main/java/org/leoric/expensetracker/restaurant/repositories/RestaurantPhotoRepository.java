package org.leoric.expensetracker.restaurant.repositories;

import org.leoric.expensetracker.restaurant.models.RestaurantPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RestaurantPhotoRepository extends JpaRepository<RestaurantPhoto, UUID> {
	List<RestaurantPhoto> findByRestaurantIdOrderByCreatedDateAscIdAsc(UUID restaurantId);
	Optional<RestaurantPhoto> findByIdAndRestaurantId(UUID id, UUID restaurantId);
}
