package org.leoric.expensetracker.vacation.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.asset.models.Asset;
import org.leoric.expensetracker.asset.models.constants.AssetType;
import org.leoric.expensetracker.asset.repositories.AssetRepository;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.leoric.expensetracker.image.services.interfaces.ImageService;
import org.leoric.expensetracker.restaurant.services.RestaurantFileCleanupService;
import org.leoric.expensetracker.vacation.dtos.VacationDtos.*;
import org.leoric.expensetracker.vacation.models.*;
import org.leoric.expensetracker.vacation.repositories.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDate;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional @Slf4j
public class VacationService {
	private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/heic", "image/gif", "image/avif");
	private final VacationRepository vacations;
	private final VacationPlaceRepository places;
	private final VacationVisitRepository visits;
	private final VacationExpenseRepository expenses;
	private final VacationPhotoRepository photos;
	private final ExpenseTrackerRepository trackers;
	private final AssetRepository assets;
	private final ImageService images;
	private final RestaurantFileCleanupService cleanup;

	@Transactional(readOnly = true)
	public Page<VacationSummary> list(UUID trackerId, String search, String status, String type, String focus,
			String country, String provider, String recommendation, Integer year, Integer minRating, Integer maxRating,
			String order, int page, int size) {
		if (page < 0 || size < 1 || size > 100) throw new OperationNotPermittedException("Invalid pagination");
		if (minRating != null && (minRating < 0 || minRating > 10) || maxRating != null && (maxRating < 0 || maxRating > 10)
				|| minRating != null && maxRating != null && minRating > maxRating) throw new OperationNotPermittedException("Invalid rating filter");
		String ordering = order == null ? "date" : order;
		if (!Set.of("date", "name", "rating").contains(ordering)) throw new OperationNotPermittedException("Invalid sort order");
		return vacations.search(trackerId, lower(search), blank(status) ? "" : status, lower(type), lower(focus),
				lower(country), lower(provider), blank(recommendation) ? "" : recommendation, year, minRating,
				maxRating, ordering, PageRequest.of(page, size)).map(this::summary);
	}

	@Transactional(readOnly = true)
	public VacationDetail detail(UUID trackerId, UUID id) {
		Vacation vacation = vacation(trackerId, id);
		return new VacationDetail(summary(vacation), visits.findByVacationIdOrderByCreatedDateAscIdAsc(id).stream().map(this::visitDto).toList(),
				expenses.findByVacationIdOrderByCreatedDateAscIdAsc(id).stream().map(this::expenseDto).toList(),
				photos.findByVacationIdAndVisitIsNullOrderByCreatedDateAscIdAsc(id).stream().map(this::photoDto).toList());
	}

	public VacationDetail create(UUID trackerId, VacationInput input) {
		var vacation = new Vacation();
		vacation.setExpenseTracker(trackers.findById(trackerId).orElseThrow(() -> new EntityNotFoundException("Tracker not found")));
		setVacation(vacation, input);
		vacations.saveAndFlush(vacation);
		return detail(trackerId, vacation.getId());
	}
	public VacationDetail update(UUID trackerId, UUID id, VacationInput input) {
		var vacation = vacation(trackerId, id);
		setVacation(vacation, input);
		return detail(trackerId, id);
	}
	private void setVacation(Vacation v, VacationInput in) {
		choice(in.status(), Set.of("PLANNED", "COMPLETED", "CANCELLED"), "status");
		choice(in.recommendation(), Set.of("NORMAL", "FAVORITE", "AVOID"), "recommendation");
		dates(in.startDate(), in.endDate());
		if (in.year() != null && in.startDate() != null && in.year() != in.startDate().getYear())
			throw new OperationNotPermittedException("Year and start date do not match");
		if (in.totalMinor() != null && blank(in.currencyCode())) throw new OperationNotPermittedException("Currency is required");
		String code = blank(in.currencyCode()) ? null : currency(in.currencyCode()).getCode();
		if (v.getTotalMinor() != null && in.totalMinor() != null && !Objects.equals(v.getCurrencyCode(), code))
			throw new OperationNotPermittedException("Remove the saved total before changing currency");
		if (!Objects.equals(v.getCurrencyCode(), code)) v.setCurrencyScale(code == null ? null : currency(code).getScale());
		v.setName(in.name().trim()); v.setStatus(in.status()); v.setType(normalize(in.type()));
		v.getFocuses().clear(); in.focuses().stream().map(String::trim).distinct().forEach(v.getFocuses()::add);
		v.setYear(in.startDate() != null ? Integer.valueOf(in.startDate().getYear()) : in.year()); v.setStartDate(in.startDate());
		v.setEndDate(in.endDate()); v.setApproximate(Boolean.TRUE.equals(in.approximate())); v.setCountry(normalize(in.country()));
		v.setLocation(normalize(in.location())); v.setProvider(normalize(in.provider())); v.setBookingMethod(normalize(in.bookingMethod()));
		v.setPeople(in.people()); v.setTotalMinor(in.totalMinor()); v.setCurrencyCode(code); v.setRating(in.rating());
		v.setRecommendation(in.recommendation()); v.setNote(normalize(in.note())); v.setPositives(normalize(in.positives()));
		v.setNegatives(normalize(in.negatives())); replaceRatings(v.getRatings(), in.ratings());
	}

	@Transactional(readOnly = true)
	public List<Place> listPlaces(UUID trackerId, String search) {
		String text = lower(search);
		return places.findByExpenseTrackerId(trackerId).stream().filter(p -> text.isEmpty() || contains(p.getName(), text)
				|| contains(p.getLocation(), text) || contains(p.getCountry(), text))
				.sorted(Comparator.comparing(VacationPlace::getName, String.CASE_INSENSITIVE_ORDER).thenComparing(VacationPlace::getId))
				.map(this::placeDto).toList();
	}
	@Transactional(readOnly = true)
	public Page<Place> searchPlaces(UUID trackerId, String search, String kind, String area, String recommendation,
			Integer minRating, Integer maxRating, String order, int page, int size) {
		if (page < 0 || size < 1 || size > 100) throw new OperationNotPermittedException("Invalid pagination");
		if (minRating != null && (minRating < 0 || minRating > 10) || maxRating != null && (maxRating < 0 || maxRating > 10)
				|| minRating != null && maxRating != null && minRating > maxRating)
			throw new OperationNotPermittedException("Invalid rating filter");
		if (!Set.of("name", "rating").contains(order)) throw new OperationNotPermittedException("Invalid sort order");
		if (!blank(recommendation)) choice(recommendation, Set.of("NORMAL", "FAVORITE", "AVOID"), "recommendation");
		return places.search(trackerId, lower(search), lower(kind), lower(area), blank(recommendation) ? "" : recommendation,
				minRating, maxRating, order, PageRequest.of(page, size)).map(this::placeDto);
	}
	@Transactional(readOnly = true)
	public PlaceDetail placeDetail(UUID trackerId, UUID id) {
		VacationPlace p = place(trackerId, id);
		return new PlaceDetail(placeDto(p), visits.findByPlaceId(id).stream()
				.map(v -> new PlaceHistory(v.getVacation().getId(), v.getVacation().getName(), visitDto(v))).toList());
	}
	public Place createPlace(UUID trackerId, PlaceInput input) {
		var p = new VacationPlace();
		p.setExpenseTracker(trackers.findById(trackerId).orElseThrow(() -> new EntityNotFoundException("Tracker not found")));
		setPlace(p, input); places.saveAndFlush(p); return placeDto(p);
	}
	public Place updatePlace(UUID trackerId, UUID id, PlaceInput input) {
		var p = place(trackerId, id); setPlace(p, input); return placeDto(p);
	}
	private void setPlace(VacationPlace p, PlaceInput in) {
		choice(in.recommendation(), Set.of("NORMAL", "FAVORITE", "AVOID"), "recommendation");
		p.setName(in.name().trim()); p.setKind(in.kind().trim()); p.setCountry(normalize(in.country()));
		p.setLocation(normalize(in.location())); p.setNote(normalize(in.note())); p.setRecommendation(in.recommendation());
	}
	public void deletePlace(UUID trackerId, UUID id) {
		var p = place(trackerId, id);
		visits.findByPlaceId(id).forEach(v -> v.setPlace(null));
		visits.flush(); places.delete(p);
	}
	public Visit createVisit(UUID trackerId, UUID vacationId, VisitInput input) {
		if (input.placeId() == null) throw new OperationNotPermittedException("Place is required");
		var v = new VacationVisit(); v.setVacation(vacation(trackerId, vacationId)); setVisit(v, trackerId, input);
		visits.saveAndFlush(v); return visitDto(v);
	}
	public Visit updateVisit(UUID trackerId, UUID vacationId, UUID visitId, VisitInput input) {
		vacation(trackerId, vacationId); var v = visit(vacationId, visitId);
		if (input.placeId() == null && v.getPlace() != null) throw new OperationNotPermittedException("Place is required");
		setVisit(v, trackerId, input); return visitDto(v);
	}
	private void setVisit(VacationVisit v, UUID trackerId, VisitInput in) {
		dates(in.startDate(), in.endDate());
		if (in.placeId() != null) {
			var p = place(trackerId, in.placeId());
			if (v.getPlace() == null || !v.getPlace().getId().equals(p.getId())) {
				v.setNameSnapshot(p.getName()); v.setKindSnapshot(p.getKind());
			}
			v.setPlace(p);
		}
		v.setStartDate(in.startDate()); v.setEndDate(in.endDate()); v.setRating(in.rating()); v.setNote(normalize(in.note()));
		v.setPositives(normalize(in.positives())); v.setNegatives(normalize(in.negatives())); replaceRatings(v.getRatings(), in.ratings());
	}
	public Expense createExpense(UUID trackerId, UUID vacationId, ExpenseInput input) {
		var e = new VacationExpense(); e.setVacation(vacation(trackerId, vacationId)); setExpense(e, input);
		expenses.saveAndFlush(e); return expenseDto(e);
	}
	public Expense updateExpense(UUID trackerId, UUID vacationId, UUID expenseId, ExpenseInput input) {
		vacation(trackerId, vacationId);
		var e = expenses.findByIdAndVacationId(expenseId, vacationId).orElseThrow(() -> new EntityNotFoundException("Expense not found"));
		setExpense(e, input); return expenseDto(e);
	}
	private void setExpense(VacationExpense e, ExpenseInput in) {
		Asset a = currency(in.currencyCode());
		if (e.getId() != null && !e.getCurrencyCode().equals(a.getCode()))
			throw new OperationNotPermittedException("Create a new expense to change currency");
		if (e.getId() == null) e.setCurrencyScale(a.getScale());
		e.setName(in.name().trim()); e.setKind(normalize(in.kind())); e.setAmountMinor(in.amountMinor()); e.setCurrencyCode(a.getCode());
		e.setExpenseDate(in.expenseDate()); e.setNote(normalize(in.note()));
	}
	public void deleteExpense(UUID trackerId, UUID vacationId, UUID expenseId) {
		vacation(trackerId, vacationId);
		expenses.delete(expenses.findByIdAndVacationId(expenseId, vacationId).orElseThrow(() -> new EntityNotFoundException("Expense not found")));
	}
	public Photo addPhoto(UUID trackerId, UUID vacationId, UUID visitId, MultipartFile file) {
		var vacation = vacation(trackerId, vacationId);
		var visit = visitId == null ? null : visit(vacationId, visitId);
		if (file == null || file.isEmpty() || !IMAGE_TYPES.contains(file.getContentType()))
			throw new OperationNotPermittedException("A supported image is required");
		String url = images.uploadImage(file, "expense-tracker/vacations");
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override public void afterCompletion(int status) {
				if (status != STATUS_COMMITTED) try { cleanup.enqueueAfterRollback(url); cleanup.process(); }
				catch (RuntimeException e) { log.error("Failed to queue uncommitted vacation image {}", url, e); }
			}
		});
		var photo = new VacationPhoto(); photo.setVacation(vacation); photo.setVisit(visit); photo.setImageUrl(url);
		photos.saveAndFlush(photo); return photoDto(photo);
	}
	public void deletePhoto(UUID trackerId, UUID vacationId, UUID photoId) {
		vacation(trackerId, vacationId);
		var photo = photos.findByIdAndVacationId(photoId, vacationId).orElseThrow(() -> new EntityNotFoundException("Photo not found"));
		cleanup.enqueue(photo.getImageUrl()); photos.delete(photo);
	}
	public void deleteVisit(UUID trackerId, UUID vacationId, UUID visitId) {
		vacation(trackerId, vacationId); removeVisit(visit(vacationId, visitId));
	}
	private void removeVisit(VacationVisit v) {
		for (var photo : photos.findByVisitIdOrderByCreatedDateAscIdAsc(v.getId())) { cleanup.enqueue(photo.getImageUrl()); photos.delete(photo); }
		photos.flush(); visits.delete(v); visits.flush();
	}
	public void deleteVacation(UUID trackerId, UUID vacationId) { removeVacation(vacation(trackerId, vacationId)); }
	private void removeVacation(Vacation v) {
		visits.findByVacationIdOrderByCreatedDateAscIdAsc(v.getId()).forEach(this::removeVisit);
		expenses.deleteAll(expenses.findByVacationIdOrderByCreatedDateAscIdAsc(v.getId())); expenses.flush();
		for (var photo : photos.findByVacationIdAndVisitIsNullOrderByCreatedDateAscIdAsc(v.getId())) { cleanup.enqueue(photo.getImageUrl()); photos.delete(photo); }
		photos.flush(); vacations.delete(v); vacations.flush();
	}
	public void deleteAllForTracker(UUID trackerId) {
		vacations.findByExpenseTrackerId(trackerId).forEach(this::removeVacation);
		places.deleteAll(places.findByExpenseTrackerId(trackerId)); places.flush();
	}
	private VacationSummary summary(Vacation v) {
		return new VacationSummary(v.getId(), v.getName(), v.getStatus(), v.getType(), List.copyOf(v.getFocuses()), v.getYear(),
				v.getStartDate(), v.getEndDate(), v.isApproximate(), v.getCountry(), v.getLocation(), v.getProvider(), v.getBookingMethod(),
				v.getPeople(), v.getTotalMinor(), v.getCurrencyCode(), v.getCurrencyScale(), v.getRating(), v.getRecommendation(),
				v.getNote(), v.getPositives(), v.getNegatives(), Map.copyOf(v.getRatings()));
	}
	private Place placeDto(VacationPlace p) {
		var scores = visits.findByPlaceId(p.getId()).stream().map(VacationVisit::getRating).filter(Objects::nonNull).toList();
		var avg = scores.stream().mapToInt(Integer::intValue).average();
		return new Place(p.getId(), p.getName(), p.getKind(), p.getCountry(), p.getLocation(), p.getNote(), p.getRecommendation(),
				avg.isPresent() ? avg.getAsDouble() : null, scores.stream().min(Integer::compareTo).orElse(null), scores.size());
	}
	private Visit visitDto(VacationVisit v) {
		return new Visit(v.getId(), v.getPlace() == null ? null : v.getPlace().getId(), v.getNameSnapshot(), v.getKindSnapshot(),
				v.getStartDate(), v.getEndDate(), v.getRating(), v.getNote(), v.getPositives(), v.getNegatives(), Map.copyOf(v.getRatings()),
				photos.findByVisitIdOrderByCreatedDateAscIdAsc(v.getId()).stream().map(this::photoDto).toList());
	}
	private Expense expenseDto(VacationExpense e) { return new Expense(e.getId(), e.getName(), e.getKind(), e.getAmountMinor(),
			e.getCurrencyCode(), e.getCurrencyScale(), e.getExpenseDate(), e.getNote()); }
	private Photo photoDto(VacationPhoto p) { return new Photo(p.getId(), p.getImageUrl()); }
	private Vacation vacation(UUID trackerId, UUID id) { return vacations.findByIdAndExpenseTrackerId(id, trackerId).orElseThrow(() -> new EntityNotFoundException("Vacation not found")); }
	private VacationPlace place(UUID trackerId, UUID id) { return places.findByIdAndExpenseTrackerId(id, trackerId).orElseThrow(() -> new EntityNotFoundException("Place not found")); }
	private VacationVisit visit(UUID vacationId, UUID id) { return visits.findByIdAndVacationId(id, vacationId).orElseThrow(() -> new EntityNotFoundException("Visit not found")); }
	private Asset currency(String code) { return assets.findByCodeIgnoreCase(code.trim()).filter(a -> a.getAssetType() == AssetType.FIAT)
			.orElseThrow(() -> new OperationNotPermittedException("Unknown currency")); }
	private void dates(LocalDate start, LocalDate end) { if (start != null && end != null && end.isBefore(start)) throw new OperationNotPermittedException("End date is before start date"); }
	private void choice(String value, Set<String> allowed, String field) { if (!allowed.contains(value)) throw new OperationNotPermittedException("Invalid " + field); }
	private void replaceRatings(Map<String,Integer> target, Map<String,Integer> input) {
		target.clear(); input.forEach((key, value) -> {
			String normalized = key.trim();
			if (target.keySet().stream().anyMatch(existing -> existing.equalsIgnoreCase(normalized))) throw new OperationNotPermittedException("Duplicate rating category");
			target.put(normalized, value);
		});
	}
	private boolean blank(String s) { return s == null || s.isBlank(); }
	private String normalize(String s) { return blank(s) ? null : s.trim(); }
	private String lower(String s) { return blank(s) ? "" : s.trim().toLowerCase(Locale.ROOT); }
	private boolean contains(String s, String value) { return s != null && s.toLowerCase(Locale.ROOT).contains(value); }
}
