package org.leoric.expensetracker.restaurant.repositories;

import org.leoric.expensetracker.restaurant.models.Restaurant;
import org.leoric.expensetracker.restaurant.models.Recommendation;
import org.leoric.expensetracker.restaurant.models.PriceLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RestaurantRepository extends JpaRepository<Restaurant, UUID> {
	String FILTER = """
			where r.expenseTracker.id = :trackerId
			and (:recommendation is null or r.recommendation = :recommendation)
			and (:level is null or r.priceLevel = :level)
			and (:service = '' or exists (select s from Restaurant r2 join r2.services s
				where r2.id = r.id and lower(s) = :service))
			and (:text = '' or locate(:text, lower(r.name)) > 0
				or exists (select d.id from RestaurantDish d where d.restaurant = r and locate(:text, lower(d.name)) > 0))
			and (:minRating is null or (select avg(v.rating) from RestaurantVisit v where v.restaurant = r) >= :minRating)
			and (:maxRating is null or (select avg(v.rating) from RestaurantVisit v where v.restaurant = r) <= :maxRating)
			""";

	@Query(value = """
			select r from Restaurant r
			""" + FILTER + """
			order by
			case when :ordering = 'rating' and (select avg(v.rating) from RestaurantVisit v where v.restaurant = r) is null then 1
			     when :ordering = 'recent' and (select max(v.visitedOn) from RestaurantVisit v where v.restaurant = r) is null then 1
			     else 0 end,
			case when :ordering = 'rating' then (select avg(v.rating) from RestaurantVisit v where v.restaurant = r) end desc,
			case when :ordering = 'recent' then (select max(v.visitedOn) from RestaurantVisit v where v.restaurant = r) end desc,
			case when :ordering = 'name' then lower(r.name) end asc, r.id asc
			""", countQuery = "select count(r) from Restaurant r " + FILTER)
	Page<Restaurant> search(@Param("trackerId") UUID trackerId, @Param("text") String text,
			@Param("recommendation") Recommendation recommendation, @Param("level") PriceLevel level,
			@Param("service") String service, @Param("minRating") Integer minRating,
			@Param("maxRating") Integer maxRating, @Param("ordering") String ordering, Pageable pageable);

	List<Restaurant> findByExpenseTrackerId(UUID trackerId);
	Optional<Restaurant> findByIdAndExpenseTrackerId(UUID id, UUID trackerId);
}
