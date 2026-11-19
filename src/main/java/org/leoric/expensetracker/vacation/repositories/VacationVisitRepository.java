package org.leoric.expensetracker.vacation.repositories;
import org.leoric.expensetracker.vacation.models.VacationVisit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface VacationVisitRepository extends JpaRepository<VacationVisit, UUID> {
	List<VacationVisit> findByVacationIdOrderByCreatedDateAscIdAsc(UUID vacationId);
	List<VacationVisit> findByPlaceId(UUID placeId);
	Optional<VacationVisit> findByIdAndVacationId(UUID id, UUID vacationId);
}
