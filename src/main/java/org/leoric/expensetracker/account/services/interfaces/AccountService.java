package org.leoric.expensetracker.account.services.interfaces;

import org.leoric.expensetracker.account.dto.AccountResponseDto;
import org.leoric.expensetracker.account.dto.CreateAccountRequestDto;
import org.leoric.expensetracker.account.dto.UpdateAccountRequestDto;
import org.leoric.expensetracker.auth.models.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
public interface AccountService {

	AccountResponseDto accountCreate(User currentUser, UUID trackerId, CreateAccountRequestDto request);

	AccountResponseDto accountFindById(User currentUser, UUID trackerId, UUID accountId);

	Page<AccountResponseDto> accountFindAll(User currentUser, UUID trackerId, String search, Pageable pageable);

	AccountResponseDto accountUpdate(User currentUser, UUID trackerId, UUID accountId, UpdateAccountRequestDto request);

	void accountDeactivate(User currentUser, UUID trackerId, UUID accountId);

	AccountResponseDto accountUploadIcon(User currentUser, UUID trackerId, UUID accountId, MultipartFile icon, String iconColor);

	AccountResponseDto accountDeleteIcon(User currentUser, UUID trackerId, UUID accountId);
}