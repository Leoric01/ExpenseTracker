package org.leoric.expensetracker.transaction.services.interfaces;

import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.transaction.dto.CreateTransactionRequestDto;
import org.leoric.expensetracker.transaction.dto.TransactionAttachmentResponseDto;
import org.leoric.expensetracker.transaction.dto.TransactionFilter;
import org.leoric.expensetracker.transaction.dto.TransactionResponseDto;
import org.leoric.expensetracker.transaction.dto.UpdateTransactionRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
public interface TransactionService {

	TransactionResponseDto transactionCreate(User currentUser, UUID trackerId, CreateTransactionRequestDto request);

	TransactionResponseDto transactionFindById(User currentUser, UUID trackerId, UUID transactionId);

	Page<TransactionResponseDto> transactionFindAllPageable(User currentUser, UUID trackerId, TransactionFilter filter, Pageable pageable);

	TransactionResponseDto transactionUpdate(User currentUser, UUID trackerId, UUID transactionId, UpdateTransactionRequestDto request);

	TransactionResponseDto transactionCancel(User currentUser, UUID trackerId, UUID transactionId);

	TransactionAttachmentResponseDto transactionUploadAttachment(User currentUser, UUID trackerId, UUID transactionId, MultipartFile file);

	List<TransactionAttachmentResponseDto> transactionFindAttachments(User currentUser, UUID trackerId, UUID transactionId);

	void transactionDeleteAttachment(User currentUser, UUID trackerId, UUID transactionId, UUID attachmentId);
}