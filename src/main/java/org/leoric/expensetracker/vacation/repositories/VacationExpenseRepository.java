package org.leoric.expensetracker.vacation.repositories;
import org.leoric.expensetracker.vacation.models.VacationExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface VacationExpenseRepository extends JpaRepository<VacationExpense, UUID> {
	List<VacationExpense> findByVacationIdOrderByCreatedDateAscIdAsc(UUID vacationId);
	Optional<VacationExpense> findByIdAndVacationId(UUID id, UUID vacationId);
}
