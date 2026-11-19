package org.leoric.expensetracker.vacation.repositories;
import org.leoric.expensetracker.vacation.models.Vacation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface VacationRepository extends JpaRepository<Vacation, UUID> {
	String FILTER = """
			where v.expenseTracker.id = :trackerId
			and (:text = '' or locate(:text, lower(v.name)) > 0 or locate(:text, lower(v.location)) > 0
				or exists (select x.id from VacationVisit x where x.vacation = v and locate(:text, lower(x.nameSnapshot)) > 0))
			and (:status = '' or v.status = :status)
			and (:type = '' or locate(:type, lower(v.type)) > 0)
			and (:focus = '' or exists (select vf.id from Vacation vf join vf.focuses f where vf = v and lower(f) = :focus))
			and (:country = '' or locate(:country, lower(v.country)) > 0)
			and (:provider = '' or locate(:provider, lower(v.provider)) > 0)
			and (:recommendation = '' or v.recommendation = :recommendation)
			and (:year is null or v.year = :year)
			and (:minRating is null or v.rating >= :minRating)
			and (:maxRating is null or v.rating <= :maxRating)
			""";

	@Query(value = "select v from Vacation v " + FILTER + """
			order by
			case when :ordering = 'rating' and v.rating is null then 1
			     when :ordering = 'date' and v.year is null then 1 else 0 end,
			case when :ordering = 'rating' then v.rating end desc,
			case when :ordering = 'date' then v.year end desc,
			case when :ordering = 'date' and v.startDate is null then 1 else 0 end,
			case when :ordering = 'date' then v.startDate end desc,
			case when :ordering = 'name' then lower(v.name) end asc, v.id asc
			""", countQuery = "select count(v) from Vacation v " + FILTER)
	Page<Vacation> search(@Param("trackerId") UUID trackerId, @Param("text") String text,
			@Param("status") String status, @Param("type") String type, @Param("focus") String focus,
			@Param("country") String country, @Param("provider") String provider,
			@Param("recommendation") String recommendation, @Param("year") Integer year,
			@Param("minRating") Integer minRating, @Param("maxRating") Integer maxRating,
			@Param("ordering") String ordering, Pageable pageable);
	List<Vacation> findByExpenseTrackerId(UUID trackerId);
	Optional<Vacation> findByIdAndExpenseTrackerId(UUID id, UUID trackerId);
}
