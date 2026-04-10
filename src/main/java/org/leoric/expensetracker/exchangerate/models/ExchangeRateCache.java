package org.leoric.expensetracker.exchangerate.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.leoric.expensetracker.asset.models.constants.MarketDataSource;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "exchange_rate_cache", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"base_asset_code", "quote_asset_code", "rate_date"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class ExchangeRateCache {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.AUTO)
	@Column(columnDefinition = "BINARY(16)")
	private UUID id;

	@Column(name = "base_asset_code", nullable = false, length = 20)
	private String baseAssetCode;

	@Column(name = "quote_asset_code", nullable = false, length = 20)
	private String quoteAssetCode;

	@Column(name = "rate_date", nullable = false)
	private LocalDate rateDate;

	@Column(nullable = false, precision = 24, scale = 12)
	private BigDecimal rate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private MarketDataSource source;

	@CreatedDate
	@Column(updatable = false, nullable = false)
	private Instant fetchedAt;
}