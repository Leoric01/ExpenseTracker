package org.leoric.expensetracker.vacation.repositories;
import org.leoric.expensetracker.vacation.models.VacationPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface VacationPhotoRepository extends JpaRepository<VacationPhoto, UUID> {
	List<VacationPhoto> findByVacationIdAndVisitIsNullOrderByCreatedDateAscIdAsc(UUID vacationId);
	List<VacationPhoto> findByVisitIdOrderByCreatedDateAscIdAsc(UUID visitId);
	Optional<VacationPhoto> findByIdAndVacationId(UUID id, UUID vacationId);
}
