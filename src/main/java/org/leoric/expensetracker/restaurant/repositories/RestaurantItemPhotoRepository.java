package org.leoric.expensetracker.restaurant.repositories;

import org.leoric.expensetracker.restaurant.models.RestaurantItemPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RestaurantItemPhotoRepository extends JpaRepository<RestaurantItemPhoto, UUID> {
	List<RestaurantItemPhoto> findByItemIdOrderByCreatedDateAscIdAsc(UUID itemId);
	Optional<RestaurantItemPhoto> findByIdAndItemId(UUID id, UUID itemId);
}
