package org.leoric.expensetracker.transaction.services.interfaces;

import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.transaction.dto.CreateTransactionRequestDto;
import org.leoric.expensetracker.transaction.dto.TransactionResponseDto;
import org.leoric.expensetracker.transaction.dto.UpdateTransactionRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public interface TransactionService {

	TransactionResponseDto transactionCreate(User currentUser, UUID trackerId, CreateTransactionRequestDto request);

	TransactionResponseDto transactionFindById(User currentUser, UUID trackerId, UUID transactionId);

	Page<TransactionResponseDto> transactionFindAll(User currentUser, UUID trackerId, String search, Pageable pageable);

	TransactionResponseDto transactionUpdate(User currentUser, UUID trackerId, UUID transactionId, UpdateTransactionRequestDto request);

	TransactionResponseDto transactionCancel(User currentUser, UUID trackerId, UUID transactionId);
}