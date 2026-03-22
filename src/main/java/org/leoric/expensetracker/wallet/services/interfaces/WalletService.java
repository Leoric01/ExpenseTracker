package org.leoric.expensetracker.wallet.services.interfaces;

import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.wallet.dto.CreateWalletRequestDto;
import org.leoric.expensetracker.wallet.dto.UpdateWalletRequestDto;
import org.leoric.expensetracker.wallet.dto.WalletResponseDto;
import org.leoric.expensetracker.wallet.dto.WalletDashboardResponseDto;
import org.leoric.expensetracker.wallet.dto.WalletSummaryResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.UUID;

@Service
public interface WalletService {

	WalletResponseDto walletCreate(User currentUser, UUID trackerId, CreateWalletRequestDto request);

	WalletResponseDto walletFindById(User currentUser, UUID trackerId, UUID walletId);

	Page<WalletResponseDto> walletFindAll(User currentUser, UUID trackerId, String search, Pageable pageable);

	WalletResponseDto walletUpdate(User currentUser, UUID trackerId, UUID walletId, UpdateWalletRequestDto request);

	void walletDeactivate(User currentUser, UUID trackerId, UUID walletId);

	WalletResponseDto walletUploadIcon(User currentUser, UUID trackerId, UUID walletId, MultipartFile icon, String iconColor);

	WalletResponseDto walletDeleteIcon(User currentUser, UUID trackerId, UUID walletId);

	WalletSummaryResponseDto walletSummary(User currentUser, UUID trackerId, UUID walletId, Instant from, Instant to);

	WalletDashboardResponseDto walletDashboard(User currentUser, UUID trackerId, Instant from, Instant to);
}