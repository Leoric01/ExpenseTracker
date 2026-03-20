package org.leoric.expensetracker.expensetracker.services.interfaces;

import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.dto.CreateExpenseTrackerRequest;
import org.leoric.expensetracker.expensetracker.dto.ExpenseTrackerResponse;
import org.leoric.expensetracker.expensetracker.dto.UpdateExpenseTrackerRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public interface ExpenseTrackerService {

	ExpenseTrackerResponse expenseTrackerCreate(User currentUser, CreateExpenseTrackerRequest request);

	ExpenseTrackerResponse expenseTrackerFindById(User currentUser, UUID id);

	Page<ExpenseTrackerResponse> expenseTrackerFindAll(User currentUser, String search, Pageable pageable);

	ExpenseTrackerResponse expenseTrackerUpdate(User currentUser, UUID id, UpdateExpenseTrackerRequest request);

	void expenseTrackerDeactivate(User currentUser, UUID id);
}