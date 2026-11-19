package org.leoric.expensetracker.vacation.dtos;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;

public final class VacationDtos {
	private VacationDtos() {}
	public record VacationInput(@NotBlank @Size(max=180) String name, @NotBlank String status,
			@Size(max=80) String type, @NotNull List<@NotBlank @Size(max=80) String> focuses,
			@Min(1) @Max(9999) Integer year, LocalDate startDate, LocalDate endDate, Boolean approximate,
			@Size(max=120) String country, @Size(max=180) String location, @Size(max=180) String provider,
			@Size(max=180) String bookingMethod, @Positive Integer people,
			@PositiveOrZero @Max(9007199254740991L) Long totalMinor, @Size(max=20) String currencyCode,
			@Min(0) @Max(10) Integer rating, @NotBlank String recommendation, @Size(max=4000) String note,
			@Size(max=2000) String positives, @Size(max=2000) String negatives,
			@NotNull Map<@NotBlank @Size(max=80) String, @NotNull @Min(0) @Max(10) Integer> ratings) {}
	public record PlaceInput(@NotBlank @Size(max=180) String name, @NotBlank @Size(max=80) String kind,
			@Size(max=120) String country, @Size(max=180) String location, @Size(max=4000) String note,
			@NotBlank String recommendation) {}
	public record VisitInput(UUID placeId, LocalDate startDate, LocalDate endDate, @Min(0) @Max(10) Integer rating,
			@Size(max=4000) String note, @Size(max=2000) String positives, @Size(max=2000) String negatives,
			@NotNull Map<@NotBlank @Size(max=80) String, @NotNull @Min(0) @Max(10) Integer> ratings) {}
	public record ExpenseInput(@NotBlank @Size(max=180) String name, @Size(max=80) String kind,
			@NotNull @PositiveOrZero @Max(9007199254740991L) Long amountMinor, @NotBlank @Size(max=20) String currencyCode,
			LocalDate expenseDate, @Size(max=2000) String note) {}
	public record Photo(UUID id, String imageUrl) {}
	public record Place(UUID id, String name, String kind, String country, String location, String note,
			String recommendation, Double averageRating, Integer worstRating, long ratingCount) {}
	public record Visit(UUID id, UUID placeId, String name, String kind, LocalDate startDate, LocalDate endDate,
			Integer rating, String note, String positives, String negatives, Map<String,Integer> ratings, List<Photo> photos) {}
	public record Expense(UUID id, String name, String kind, Long amountMinor, String currencyCode, int currencyScale,
			LocalDate expenseDate, String note) {}
	public record VacationSummary(UUID id, String name, String status, String type, List<String> focuses, Integer year,
			LocalDate startDate, LocalDate endDate, boolean approximate, String country, String location, String provider,
			String bookingMethod, Integer people, Long totalMinor, String currencyCode, Integer currencyScale,
			Integer rating, String recommendation, String note, String positives, String negatives, Map<String,Integer> ratings) {}
	public record VacationDetail(VacationSummary vacation, List<Visit> visits, List<Expense> expenses, List<Photo> photos) {}
	public record PlaceHistory(UUID vacationId, String vacationName, Visit visit) {}
	public record PlaceDetail(Place place, List<PlaceHistory> history) {}
	public record DeleteResult(long pendingCleanup) {}
}
