package org.leoric.expensetracker.restaurant.repositories;

import org.leoric.expensetracker.restaurant.models.RestaurantVisitItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RestaurantVisitItemRepository extends JpaRepository<RestaurantVisitItem, UUID> {
	List<RestaurantVisitItem> findByVisitId(UUID visitId);
	List<RestaurantVisitItem> findByDishId(UUID dishId);
	Optional<RestaurantVisitItem> findByIdAndVisitId(UUID id, UUID visitId);
}
