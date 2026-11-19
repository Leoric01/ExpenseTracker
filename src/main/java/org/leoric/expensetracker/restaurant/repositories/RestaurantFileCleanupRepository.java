package org.leoric.expensetracker.restaurant.repositories;

import org.leoric.expensetracker.restaurant.models.RestaurantFileCleanup;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantFileCleanupRepository extends JpaRepository<RestaurantFileCleanup, Long> {}
