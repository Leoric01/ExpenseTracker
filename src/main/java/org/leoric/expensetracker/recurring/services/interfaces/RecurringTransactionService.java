package org.leoric.expensetracker.recurring.services.interfaces;

import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.recurring.dto.CreateRecurringTransactionRequestDto;
import org.leoric.expensetracker.recurring.dto.RecurringTransactionResponseDto;
import org.leoric.expensetracker.recurring.dto.UpdateRecurringTransactionRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public interface RecurringTransactionService {

	RecurringTransactionResponseDto recurringTransactionCreate(User currentUser, UUID trackerId, CreateRecurringTransactionRequestDto request);

	RecurringTransactionResponseDto recurringTransactionFindById(User currentUser, UUID trackerId, UUID templateId);

	Page<RecurringTransactionResponseDto> recurringTransactionFindAll(User currentUser, UUID trackerId, String search, Pageable pageable);

	Page<RecurringTransactionResponseDto> recurringTransactionFindAllActive(User currentUser, UUID trackerId, String search, Pageable pageable);

	RecurringTransactionResponseDto recurringTransactionUpdate(User currentUser, UUID trackerId, UUID templateId, UpdateRecurringTransactionRequestDto request);

	void recurringTransactionDeactivate(User currentUser, UUID trackerId, UUID templateId);
}