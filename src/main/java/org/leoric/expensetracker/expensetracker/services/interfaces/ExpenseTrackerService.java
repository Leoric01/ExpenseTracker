package org.leoric.expensetracker.expensetracker.services.interfaces;

import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.dto.CreateExpenseTrackerRequestDto;
import org.leoric.expensetracker.expensetracker.dto.ExpenseTrackerMineResponseDto;
import org.leoric.expensetracker.expensetracker.dto.ExpenseTrackerResponseDto;
import org.leoric.expensetracker.expensetracker.dto.UpdateExpenseTrackerRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public interface ExpenseTrackerService {

	ExpenseTrackerResponseDto expenseTrackerCreate(User currentUser, CreateExpenseTrackerRequestDto request);

	ExpenseTrackerResponseDto expenseTrackerFindById(User currentUser, UUID id);

	Page<ExpenseTrackerResponseDto> expenseTrackerFindAll(User currentUser, String search, Pageable pageable);

	Page<ExpenseTrackerMineResponseDto> expenseTrackerFindAllMine(User currentUser, String search, Pageable pageable);

	ExpenseTrackerResponseDto expenseTrackerUpdate(User currentUser, UUID id, UpdateExpenseTrackerRequestDto request);

	void expenseTrackerDeactivate(User currentUser, UUID id);
}