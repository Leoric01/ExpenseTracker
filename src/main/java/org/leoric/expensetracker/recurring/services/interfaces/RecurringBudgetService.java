package org.leoric.expensetracker.recurring.services.interfaces;

import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.recurring.dto.CreateRecurringBudgetRequestDto;
import org.leoric.expensetracker.recurring.dto.RecurringBudgetResponseDto;
import org.leoric.expensetracker.recurring.dto.SyncRecurringBudgetResponseDto;
import org.leoric.expensetracker.recurring.dto.UpdateRecurringBudgetRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public interface RecurringBudgetService {

	RecurringBudgetResponseDto recurringBudgetCreate(User currentUser, UUID trackerId, CreateRecurringBudgetRequestDto request);

	RecurringBudgetResponseDto recurringBudgetFindById(User currentUser, UUID trackerId, UUID templateId);

	Page<RecurringBudgetResponseDto> recurringBudgetFindAll(User currentUser, UUID trackerId, String search, Pageable pageable);

	Page<RecurringBudgetResponseDto> recurringBudgetFindAllActive(User currentUser, UUID trackerId, String search, Pageable pageable);

	RecurringBudgetResponseDto recurringBudgetUpdate(User currentUser, UUID trackerId, UUID templateId, UpdateRecurringBudgetRequestDto request);

	void recurringBudgetDeactivate(User currentUser, UUID trackerId, UUID templateId);

	SyncRecurringBudgetResponseDto syncRecurringBudgets(User currentUser, UUID trackerId);
}