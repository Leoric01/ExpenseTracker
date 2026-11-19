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
import org.leoric.expensetracker.holding.dto.HoldingPeriodPerformanceDto;
import org.leoric.expensetracker.holding.dto.InvestmentPerformanceDto;
import org.leoric.expensetracker.holding.models.Holding;
import org.leoric.expensetracker.holding.repositories.HoldingRepository;
import org.leoric.expensetracker.holding.services.HoldingSummaryConversionService;
import org.leoric.expensetracker.holding.services.PortfolioPerformanceService;
import org.leoric.expensetracker.holding.services.interfaces.HoldingSummaryBuilder;
import org.leoric.expensetracker.image.services.interfaces.ImageService;
import org.leoric.expensetracker.institution.dto.AccountSummaryResponseDto;
import org.leoric.expensetracker.institution.dto.CreateInstitutionRequestDto;
import org.leoric.expensetracker.institution.dto.FinanceHeaderBalancesResponse;
import org.leoric.expensetracker.institution.dto.FinanceHeaderNativeBalanceRow;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
	private final HoldingSummaryConversionService holdingSummaryConversionService;
	private final PortfolioPerformanceService portfolioPerformanceService;

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

		PortfolioPerformanceService.Result performance = displayAsset == null ? null
				: portfolioPerformanceService.calculate(trackerId, displayAsset, to);

		List<InstitutionSummaryResponseDto> institutionSummaries = institutions.stream()
				.map(inst -> buildInstitutionSummary(inst, from, to, displayAsset, performance))
				.toList();

		List<WidgetItemResponseDto> widgetOrder = widgetItemService.widgetItemFindAll(currentUser, WidgetType.INSTITUTION);

		Long grandTotalConverted = null;
		if (displayAsset != null && institutionSummaries.stream().allMatch(inst -> inst.convertedTotalBalance() != null)) {
			grandTotalConverted = institutionSummaries.stream()
					.map(InstitutionSummaryResponseDto::convertedTotalBalance)
					.mapToLong(Long::longValue)
					.reduce(0L, Math::addExact);
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
				grandTotalConverted,
				performance != null ? performance.portfolio() : null,
				displayAsset != null ? aggregatePeriod(institutionSummaries.stream()
						.flatMap(inst -> inst.accounts().stream()).flatMap(account -> account.holdings().stream()).toList()) : null
		);
	}

	@Override
	@Transactional(readOnly = true)
	public FinanceHeaderBalancesResponse institutionHeaderBalances(User currentUser, UUID trackerId, Instant from, Instant to) {
		ExpenseTracker tracker = getTrackerOrThrow(trackerId);
		Asset displayAsset = tracker.getPreferredDisplayAsset();
		Instant now = Instant.now();
		LocalDate endDate = (to.isBefore(now) ? to : now).atZone(ZoneOffset.UTC).toLocalDate();

		List<Holding> holdings = holdingRepository.findByExpenseTrackerIdAndFullyActiveHierarchy(trackerId);
		Map<AssetKey, Long> nativeBalancesByAsset = new HashMap<>();

		long convertedTotal = 0L;
		boolean conversionComplete = true;
		for (Holding holding : holdings) {
			HoldingSummaryResponseDto summary = holdingSummaryBuilder.buildSummary(holding, from, to);

			AssetKey assetKey = new AssetKey(holding.getAsset().getCode(), holding.getAsset().getScale());
			nativeBalancesByAsset.merge(assetKey, summary.endBalance(), Math::addExact);

			if (displayAsset != null) {
				if (holding.getAsset().getCode().equalsIgnoreCase(displayAsset.getCode())) {
					convertedTotal = Math.addExact(convertedTotal, summary.endBalance());
				} else {
					Long convertedEndBalance = convertEndBalance(summary, holding.getAsset(), displayAsset, endDate);
					if (convertedEndBalance != null) {
						convertedTotal = Math.addExact(convertedTotal, convertedEndBalance);
					} else {
						conversionComplete = false;
					}
				}
			}
		}

		List<FinanceHeaderNativeBalanceRow> nativeBalances = nativeBalancesByAsset.entrySet().stream()
				.sorted(Comparator
						.comparing((Map.Entry<AssetKey, Long> entry) -> entry.getKey().assetCode())
						.thenComparing(entry -> entry.getKey().assetScale()))
				.map(entry -> new FinanceHeaderNativeBalanceRow(
						entry.getKey().assetCode(),
						entry.getKey().assetScale(),
						entry.getValue()))
				.toList();

		Long grandTotalConverted = displayAsset != null && conversionComplete ? convertedTotal : null;

		return new FinanceHeaderBalancesResponse(
				from,
				to,
				displayAsset != null ? displayAsset.getCode() : null,
				displayAsset != null ? displayAsset.getScale() : null,
				grandTotalConverted,
				nativeBalances
		);
	}

	private InstitutionSummaryResponseDto buildInstitutionSummary(Institution institution, Instant from, Instant to,
	                                                               Asset displayAsset,
	                                                               PortfolioPerformanceService.Result performance) {
		List<Account> accounts = accountRepository.findByInstitutionIdAndActiveTrue(institution.getId());

		List<AccountSummaryResponseDto> accountSummaries = accounts.stream()
				.map(account -> buildAccountSummary(account, from, to, displayAsset, performance))
				.toList();

		long totalBalance = accountSummaries.stream()
				.mapToLong(AccountSummaryResponseDto::totalBalance)
				.reduce(0L, Math::addExact);

		Long convertedTotalBalance = null;
		if (displayAsset != null && accountSummaries.stream().allMatch(account -> account.convertedTotalBalance() != null)) {
			convertedTotalBalance = accountSummaries.stream()
					.map(AccountSummaryResponseDto::convertedTotalBalance)
					.mapToLong(Long::longValue)
					.reduce(0L, Math::addExact);
		}

		return new InstitutionSummaryResponseDto(
				institution.getId(),
				institution.getName(),
				institution.getInstitutionType(),
				institution.getIconUrl(),
				institution.getIconColor(),
				accountSummaries,
				totalBalance,
				convertedTotalBalance,
				displayAsset != null ? aggregatePeriod(accountSummaries.stream().flatMap(account -> account.holdings().stream()).toList()) : null,
				displayAsset != null ? aggregateInvestment(accountSummaries.stream().flatMap(account -> account.holdings().stream()).toList(), performance.portfolio().asOf()) : null
		);
	}

	private AccountSummaryResponseDto buildAccountSummary(Account account, Instant from, Instant to,
	                                                       Asset displayAsset,
	                                                       PortfolioPerformanceService.Result performance) {
		List<Holding> holdings = holdingRepository.findByAccountIdAndActiveTrue(account.getId());

		List<HoldingSummaryResponseDto> holdingSummaries = holdings.stream()
				.map(holding -> {
					HoldingSummaryResponseDto summary = holdingSummaryBuilder.buildSummary(holding, from, to);
					if (displayAsset != null) {
						return holdingSummaryConversionService.enrich(summary, holding, displayAsset, performance);
					}
					return summary;
				})
				.toList();

		long totalBalance = holdingSummaries.stream()
				.mapToLong(HoldingSummaryResponseDto::endBalance)
				.reduce(0L, Math::addExact);

		Long convertedTotalBalance = null;
		if (displayAsset != null && holdingSummaries.stream().allMatch(holding -> holding.convertedEndBalance() != null)) {
			convertedTotalBalance = holdingSummaries.stream()
					.map(HoldingSummaryResponseDto::convertedEndBalance)
					.mapToLong(Long::longValue)
					.reduce(0L, Math::addExact);
		}

		return new AccountSummaryResponseDto(
				account.getId(),
				account.getName(),
				account.getAccountType(),
				account.getIconUrl(),
				account.getIconColor(),
				holdingSummaries,
				totalBalance,
				convertedTotalBalance,
				displayAsset != null ? aggregatePeriod(holdingSummaries) : null,
				displayAsset != null ? aggregateInvestment(holdingSummaries, performance.portfolio().asOf()) : null
		);
	}

	private HoldingPeriodPerformanceDto aggregatePeriod(List<HoldingSummaryResponseDto> holdings) {
		long income = 0, expense = 0, result = 0;
		boolean estimated = false;
		for (HoldingSummaryResponseDto holding : holdings) {
			HoldingPeriodPerformanceDto item = holding.periodPerformance();
			if (item == null || item.convertedIncome() == null || item.convertedExpense() == null || item.result() == null) {
				return new HoldingPeriodPerformanceDto(null, null, null, true,
						item != null && item.unavailableReason() != null ? item.unavailableReason() : "Výsledek některé pozice není dostupný.");
			}
			income = Math.addExact(income, item.convertedIncome());
			expense = Math.addExact(expense, item.convertedExpense());
			result = Math.addExact(result, item.result());
			estimated |= item.estimated();
		}
		return new HoldingPeriodPerformanceDto(income, expense, result, estimated, null);
	}

	private InvestmentPerformanceDto aggregateInvestment(List<HoldingSummaryResponseDto> holdings, Instant asOf) {
		long realized = 0, unrealized = 0;
		boolean estimated = false, artificial = false;
		for (HoldingSummaryResponseDto holding : holdings) {
			InvestmentPerformanceDto item = holding.investmentPerformance();
			if (item == null || item.realized() == null || item.unrealized() == null) {
				return new InvestmentPerformanceDto(null, null, null, true, artificial,
						item != null && item.unavailableReason() != null ? item.unavailableReason() : "Výsledek některé pozice není dostupný.", asOf);
			}
			realized = Math.addExact(realized, item.realized());
			unrealized = Math.addExact(unrealized, item.unrealized());
			estimated |= item.estimated();
			artificial |= item.artificialAllocation();
		}
		return new InvestmentPerformanceDto(realized, unrealized, Math.addExact(realized, unrealized), estimated, artificial, null, asOf);
	}

	private Long convertEndBalance(HoldingSummaryResponseDto summary, Asset holdingAsset, Asset displayAsset, LocalDate endDate) {
		if (summary.endBalance() == 0) {
			return 0L;
		}
		BigDecimal endRate = exchangeRateService.getRate(holdingAsset, displayAsset, endDate);
		if (endRate == null) {
			return null;
		}

		return exchangeRateService.convertAmount(summary.endBalance(), holdingAsset, displayAsset, endDate);
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

	private record AssetKey(String assetCode, int assetScale) {
	}
}