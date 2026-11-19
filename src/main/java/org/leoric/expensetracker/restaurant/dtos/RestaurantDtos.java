package org.leoric.expensetracker.restaurant.dtos;

import jakarta.validation.constraints.*;
import org.leoric.expensetracker.restaurant.models.PriceLevel;
import org.leoric.expensetracker.restaurant.models.Recommendation;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class RestaurantDtos {
	private RestaurantDtos() {}

	public record RestaurantInput(@NotBlank @Size(max = 180) String name, @Size(max = 2000) String note,
			PriceLevel priceLevel, @NotNull Recommendation recommendation,
			@NotNull List<@NotBlank @Size(max = 80) String> services) {}
	public record DishInput(@NotBlank @Size(max = 180) String name, @NotNull Recommendation recommendation) {}
	public record ServiceInput(@NotBlank @Size(max = 80) String name) {}
	public record VisitInput(LocalDate visitedOn, @Size(max = 80) String serviceName, @Size(max = 2000) String note,
			@Min(0) @Max(10) Integer rating, @NotBlank @Size(max = 20) String currencyCode,
			@PositiveOrZero @Max(9007199254740991L) Long totalMinor, @PositiveOrZero @Max(9007199254740991L) Long deliveryMinor) {}
	public record ItemInput(UUID dishId, @PositiveOrZero @Max(9007199254740991L) Long priceMinor, @Min(0) @Max(10) Integer rating,
			@Size(max = 2000) String note) {}
	public record Photo(UUID id, String imageUrl) {}
	public record Dish(UUID id, String name, Recommendation recommendation, Double averageRating, Integer worstRating,
			long ratingCount) {}
	public record Item(UUID id, UUID dishId, String name, Recommendation recommendation, Long priceMinor,
			Integer rating, String note, List<Photo> photos) {}
	public record Visit(UUID id, LocalDate visitedOn, String serviceName, String note, Integer rating,
			String currencyCode, int currencyScale, Long totalMinor, Long deliveryMinor, Instant createdDate,
			List<Item> items) {}
	public record RestaurantSummary(UUID id, String name, String note, PriceLevel priceLevel, Recommendation recommendation,
			List<String> services, Double averageRating, Integer worstRating, long ratingCount, LocalDate lastVisit,
			List<Photo> photos) {}
	public record RestaurantDetail(RestaurantSummary restaurant, List<Dish> dishes, List<Visit> visits) {}
	public record ServiceTag(UUID id, String name, boolean builtIn) {}
	public record DeleteResult(long pendingCleanup) {}
}
