package org.leoric.expensetracker.budget.services.interfaces;

import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.budget.dto.BudgetPlanResponseDto;
import org.leoric.expensetracker.budget.dto.BulkBudgetImportItemDto;
import org.leoric.expensetracker.budget.dto.BulkBudgetImportResponseDto;
import org.leoric.expensetracker.budget.dto.CreateBudgetPlanRequestDto;
import org.leoric.expensetracker.budget.dto.UpdateBudgetPlanRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public interface BudgetPlanService {

	BudgetPlanResponseDto budgetPlanCreate(User currentUser, UUID trackerId, CreateBudgetPlanRequestDto request);

	BudgetPlanResponseDto budgetPlanFindById(User currentUser, UUID trackerId, UUID budgetPlanId);

	Page<BudgetPlanResponseDto> budgetPlanFindAll(User currentUser, UUID trackerId, String search, Pageable pageable);

	Page<BudgetPlanResponseDto> budgetPlanFindAllActive(User currentUser, UUID trackerId, String search, Pageable pageable);

	BudgetPlanResponseDto budgetPlanUpdate(User currentUser, UUID trackerId, UUID budgetPlanId, UpdateBudgetPlanRequestDto request);

	void budgetPlanDeactivate(User currentUser, UUID trackerId, UUID budgetPlanId);

	BulkBudgetImportResponseDto bulkImport(User currentUser, UUID trackerId, List<BulkBudgetImportItemDto> items);
}