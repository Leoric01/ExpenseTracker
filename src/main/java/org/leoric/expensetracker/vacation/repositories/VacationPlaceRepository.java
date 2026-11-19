package org.leoric.expensetracker.vacation.repositories;
import org.leoric.expensetracker.vacation.models.VacationPlace;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface VacationPlaceRepository extends JpaRepository<VacationPlace, UUID> {
	String FILTER = """
			where p.expenseTracker.id = :trackerId
			and (:text = '' or locate(:text, lower(p.name)) > 0)
			and (:kind = '' or locate(:kind, lower(p.kind)) > 0)
			and (:area = '' or locate(:area, lower(p.country)) > 0 or locate(:area, lower(p.location)) > 0)
			and (:recommendation = '' or p.recommendation = :recommendation)
			and (:minRating is null or (select avg(v.rating) from VacationVisit v where v.place = p) >= :minRating)
			and (:maxRating is null or (select avg(v.rating) from VacationVisit v where v.place = p) <= :maxRating)
			""";

	@Query(value = "select p from VacationPlace p " + FILTER + """
			order by
			case when :ordering = 'rating' and (select avg(v.rating) from VacationVisit v where v.place = p) is null then 1 else 0 end,
			case when :ordering = 'rating' then (select avg(v.rating) from VacationVisit v where v.place = p) end desc,
			lower(p.name) asc, p.id asc
			""", countQuery = "select count(p) from VacationPlace p " + FILTER)
	Page<VacationPlace> search(@Param("trackerId") UUID trackerId, @Param("text") String text,
			@Param("kind") String kind, @Param("area") String area, @Param("recommendation") String recommendation,
			@Param("minRating") Integer minRating, @Param("maxRating") Integer maxRating,
			@Param("ordering") String ordering, Pageable pageable);

	List<VacationPlace> findByExpenseTrackerId(UUID trackerId);
	Optional<VacationPlace> findByIdAndExpenseTrackerId(UUID id, UUID trackerId);
}
