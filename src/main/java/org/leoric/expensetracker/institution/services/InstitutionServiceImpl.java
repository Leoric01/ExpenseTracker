package org.leoric.expensetracker.institution.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.account.models.Account;
import org.leoric.expensetracker.account.repositories.AccountRepository;
import org.leoric.expensetracker.asset.models.Asset;
import org.leoric.expensetracker.auth.dto.WidgetItemResponseDto;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.auth.models.constants.WidgetType;
import org.leoric.expensetracker.auth.services.interfaces.WidgetItemService;
import org.leoric.expensetracker.exchangerate.services.interfaces.ExchangeRateService;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.leoric.expensetracker.holding.dto.HoldingSummaryResponseDto;
import org.leoric.expensetracker.holding.models.Holding;
import org.leoric.expensetracker.holding.repositories.HoldingRepository;
import org.leoric.expensetracker.holding.services.interfaces.HoldingSummaryBuilder;
import org.leoric.expensetracker.image.services.interfaces.ImageService;
import org.leoric.expensetracker.institution.dto.AccountSummaryResponseDto;
import org.leoric.expensetracker.institution.dto.CreateInstitutionRequestDto;
import org.leoric.expensetracker.institution.dto.InstitutionDashboardResponseDto;
import org.leoric.expensetracker.institution.dto.InstitutionResponseDto;
import org.leoric.expensetracker.institution.dto.InstitutionSummaryResponseDto;
import org.leoric.expensetracker.institution.dto.UpdateInstitutionRequestDto;
import org.leoric.expensetracker.institution.mapstruct.InstitutionMapper;
import org.leoric.expensetracker.institution.models.Institution;
import org.leoric.expensetracker.institution.repositories.InstitutionRepository;
import org.leoric.expensetracker.institution.services.interfaces.InstitutionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class InstitutionServiceImpl implements InstitutionService {

	private final InstitutionRepository institutionRepository;
	private final ExpenseTrackerRepository expenseTrackerRepository;
	private final AccountRepository accountRepository;
	private final HoldingRepository holdingRepository;
	private final HoldingSummaryBuilder holdingSummaryBuilder;
	private final WidgetItemService widgetItemService;
	private final InstitutionMapper institutionMapper;
	private final ImageService imageService;
	private final ExchangeRateService exchangeRateService;

	@Override
	@Transactional
	public InstitutionResponseDto institutionCreate(User currentUser, UUID trackerId, CreateInstitutionRequestDto request) {
		ExpenseTracker tracker = getTrackerOrThrow(trackerId);

		if (institutionRepository.existsByExpenseTrackerIdAndNameIgnoreCase(trackerId, request.name())) {
			throw new OperationNotPermittedException(
					"Institution with name '%s' already exists in this expense tracker".formatted(request.name()));
		}

		Institution institution = Institution.builder()
				.expenseTracker(tracker)
				.name(request.name())
				.institutionType(request.institutionType())
				.description(request.description())
				.build();

		institution = institutionRepository.save(institution);
		log.info("User {} created institution '{}' in tracker '{}'",
				currentUser.getEmail(), institution.getName(), tracker.getName());
		return institutionMapper.toResponse(institution);
	}

	@Override
	@Transactional(readOnly = true)
	public InstitutionResponseDto institutionFindById(User currentUser, UUID trackerId, UUID institutionId) {
		Institution institution = getInstitutionOrThrow(institutionId);
		assertInstitutionBelongsToTracker(institution, trackerId);
		return institutionMapper.toResponse(institution);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<InstitutionResponseDto> institutionFindAll(User currentUser, UUID trackerId, String search, Pageable pageable) {
		if (search != null && !search.isBlank()) {
			return institutionRepository.findByExpenseTrackerIdAndActiveTrueWithSearch(trackerId, search, pageable)
					.map(institutionMapper::toResponse);
		}
		return institutionRepository.findByExpenseTrackerIdAndActiveTrue(trackerId, pageable)
				.map(institutionMapper::toResponse);
	}

	@Override
	@Transactional
	public InstitutionResponseDto institutionUpdate(User currentUser, UUID trackerId, UUID institutionId, UpdateInstitutionRequestDto request) {
		Institution institution = getInstitutionOrThrow(institutionId);
		assertInstitutionBelongsToTracker(institution, trackerId);

		institutionMapper.updateFromDto(request, institution);
		institution = institutionRepository.save(institution);

		log.info("User {} updated institution '{}' in tracker '{}'",
				currentUser.getEmail(), institution.getName(), institution.getExpenseTracker().getName());
		return institutionMapper.toResponse(institution);
	}

	@Override
	@Transactional
	public void institutionDeactivate(User currentUser, UUID trackerId, UUID institutionId) {
		Institution institution = getInstitutionOrThrow(institutionId);
		assertInstitutionBelongsToTracker(institution, trackerId);

		if (!institution.isActive()) {
			throw new OperationNotPermittedException("Institution is already deactivated");
		}

		institution.setActive(false);
		institutionRepository.save(institution);
		log.info("User {} deactivated institution '{}' in tracker '{}'",
				currentUser.getEmail(), institution.getName(), institution.getExpenseTracker().getName());
	}

	@Override
	@Transactional
	public InstitutionResponseDto institutionUploadIcon(User currentUser, UUID trackerId, UUID institutionId, MultipartFile icon, String iconColor) {
		Institution institution = getInstitutionOrThrow(institutionId);
		assertInstitutionBelongsToTracker(institution, trackerId);

		String iconUrl = imageService.uploadImage(icon, "expense-tracker/institutions");
		institution.setIconUrl(iconUrl);
		institution.setIconColor(iconColor);
		institution = institutionRepository.save(institution);

		log.info("User {} uploaded icon for institution '{}' in tracker '{}'",
				currentUser.getEmail(), institution.getName(), institution.getExpenseTracker().getName());
		return institutionMapper.toResponse(institution);
	}

	@Override
	@Transactional
	public InstitutionResponseDto institutionDeleteIcon(User currentUser, UUID trackerId, UUID institutionId) {
		Institution institution = getInstitutionOrThrow(institutionId);
		assertInstitutionBelongsToTracker(institution, trackerId);

		institution.setIconUrl(null);
		institution.setIconColor(null);
		institution = institutionRepository.save(institution);

		log.info("User {} deleted icon for institution '{}' in tracker '{}'",
				currentUser.getEmail(), institution.getName(), institution.getExpenseTracker().getName());
		return institutionMapper.toResponse(institution);
	}

	@Override
	@Transactional(readOnly = true)
	public InstitutionDashboardResponseDto institutionDashboard(User currentUser, UUID trackerId, Instant from, Instant to) {
		ExpenseTracker tracker = getTrackerOrThrow(trackerId);
		Asset displayAsset = tracker.getPreferredDisplayAsset();

		List<Institution> institutions = institutionRepository.findByExpenseTrackerIdAndActiveTrue(trackerId);

		LocalDate endDate = to.atZone(ZoneOffset.UTC).toLocalDate();

		List<InstitutionSummaryResponseDto> institutionSummaries = institutions.stream()
				.map(inst -> buildInstitutionSummary(inst, from, to, displayAsset, endDate))
				.toList();

		List<WidgetItemResponseDto> widgetOrder = widgetItemService.widgetItemFindAll(currentUser, WidgetType.INSTITUTION);

		Long grandTotalConverted = null;
		if (displayAsset != null) {
			grandTotalConverted = institutionSummaries.stream()
					.map(InstitutionSummaryResponseDto::convertedTotalBalance)
					.filter(java.util.Objects::nonNull)
					.mapToLong(Long::longValue)
					.sum();
		}

		log.debug("Built institution dashboard for tracker '{}': {} institutions, period {}-{}, displayAsset={}",
				trackerId, institutionSummaries.size(), from, to,
				displayAsset != null ? displayAsset.getCode() : "none");

		return new InstitutionDashboardResponseDto(
				from, to,
				displayAsset != null ? displayAsset.getCode() : null,
				displayAsset != null ? displayAsset.getScale() : null,
				widgetOrder,
				institutionSummaries,
				grandTotalConverted
		);
	}

	private InstitutionSummaryResponseDto buildInstitutionSummary(Institution institution, Instant from, Instant to,
	                                                               Asset displayAsset, LocalDate rateDate) {
		List<Account> accounts = accountRepository.findByInstitutionIdAndActiveTrue(institution.getId());

		List<AccountSummaryResponseDto> accountSummaries = accounts.stream()
				.map(account -> buildAccountSummary(account, from, to, displayAsset, rateDate))
				.toList();

		long totalBalance = accountSummaries.stream()
				.mapToLong(AccountSummaryResponseDto::totalBalance)
				.sum();

		Long convertedTotalBalance = null;
		if (displayAsset != null) {
			convertedTotalBalance = accountSummaries.stream()
					.map(AccountSummaryResponseDto::convertedTotalBalance)
					.filter(java.util.Objects::nonNull)
					.mapToLong(Long::longValue)
					.sum();
		}

		return new InstitutionSummaryResponseDto(
				institution.getId(),
				institution.getName(),
				institution.getInstitutionType(),
				institution.getIconUrl(),
				institution.getIconColor(),
				accountSummaries,
				totalBalance,
				convertedTotalBalance
		);
	}

	private AccountSummaryResponseDto buildAccountSummary(Account account, Instant from, Instant to,
	                                                       Asset displayAsset, LocalDate rateDate) {
		List<Holding> holdings = holdingRepository.findByAccountIdAndActiveTrue(account.getId());

		List<HoldingSummaryResponseDto> holdingSummaries = holdings.stream()
				.map(holding -> {
					HoldingSummaryResponseDto summary = holdingSummaryBuilder.buildSummary(holding, from, to);
					if (displayAsset != null) {
						return enrichWithConversion(summary, holding.getAsset(), displayAsset, rateDate, from);
					}
					return summary;
				})
				.toList();

		long totalBalance = holdingSummaries.stream()
				.mapToLong(HoldingSummaryResponseDto::endBalance)
				.sum();

		Long convertedTotalBalance = null;
		if (displayAsset != null) {
			convertedTotalBalance = holdingSummaries.stream()
					.map(HoldingSummaryResponseDto::convertedEndBalance)
					.filter(java.util.Objects::nonNull)
					.mapToLong(Long::longValue)
					.sum();
		}

		return new AccountSummaryResponseDto(
				account.getId(),
				account.getName(),
				account.getAccountType(),
				account.getIconUrl(),
				account.getIconColor(),
				holdingSummaries,
				totalBalance,
				convertedTotalBalance
		);
	}

	/**
	 * Enrich a holding summary with converted balances in the display asset.
	 * Uses the end-of-period rate for endBalance and start-of-period rate for startBalance.
	 */
	private HoldingSummaryResponseDto enrichWithConversion(HoldingSummaryResponseDto summary,
	                                                       Asset holdingAsset, Asset displayAsset,
	                                                       LocalDate endDate, Instant from) {
		if (holdingAsset.getCode().equalsIgnoreCase(displayAsset.getCode())) {
			// Same currency — converted = native
			return new HoldingSummaryResponseDto(
					summary.holdingId(), summary.accountName(), summary.institutionName(),
					summary.assetCode(), summary.assetScale(), summary.periodFrom(), summary.periodTo(),
					summary.startBalance(), summary.endBalance(),
					summary.totalIncome(), summary.totalExpense(),
					summary.totalTransferIn(), summary.totalTransferOut(),
					summary.difference(), summary.incomeByCategory(), summary.expenseByCategory(),
					summary.startBalance(), summary.endBalance(), BigDecimal.ONE
			);
		}

		BigDecimal endRate = exchangeRateService.getRate(holdingAsset, displayAsset, endDate);
		if (endRate == null) {
			return summary; // can't convert, return as-is with nulls
		}

		Long convertedEnd = exchangeRateService.convertAmount(summary.endBalance(), holdingAsset, displayAsset, endDate);

		// For start balance, use the rate at the start of the period
		LocalDate startDate = from.atZone(ZoneOffset.UTC).toLocalDate();
		Long convertedStart = exchangeRateService.convertAmount(summary.startBalance(), holdingAsset, displayAsset, startDate);

		return new HoldingSummaryResponseDto(
				summary.holdingId(), summary.accountName(), summary.institutionName(),
				summary.assetCode(), summary.assetScale(), summary.periodFrom(), summary.periodTo(),
				summary.startBalance(), summary.endBalance(),
				summary.totalIncome(), summary.totalExpense(),
				summary.totalTransferIn(), summary.totalTransferOut(),
				summary.difference(), summary.incomeByCategory(), summary.expenseByCategory(),
				convertedStart, convertedEnd, endRate
		);
	}

	private ExpenseTracker getTrackerOrThrow(UUID trackerId) {
		return expenseTrackerRepository.findById(trackerId)
				.orElseThrow(() -> new EntityNotFoundException("Expense tracker not found"));
	}

	private Institution getInstitutionOrThrow(UUID institutionId) {
		return institutionRepository.findById(institutionId)
				.orElseThrow(() -> new EntityNotFoundException("Institution not found"));
	}

	private void assertInstitutionBelongsToTracker(Institution institution, UUID trackerId) {
		if (!institution.getExpenseTracker().getId().equals(trackerId)) {
			throw new EntityNotFoundException("Institution not found in this expense tracker");
		}
	}
}