package org.leoric.expensetracker.food.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.food.models.constants.BiologicalSex;
import org.leoric.expensetracker.food.models.constants.UnitSystem;
import org.leoric.expensetracker.food.utility.NutritionConstants;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "nutrition_profile", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"expense_tracker_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class NutritionProfile {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.AUTO)
	@Column(columnDefinition = "BINARY(16)")
	private UUID id;

	@OneToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "expense_tracker_id", nullable = false, unique = true)
	private ExpenseTracker expenseTracker;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UnitSystem preferredUnitSystem;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private BiologicalSex biologicalSex;

	@Column(nullable = false, precision = 6, scale = 2)
	private BigDecimal heightCm;

	@Builder.Default
	@Column(nullable = false, precision = 4, scale = 2)
	private BigDecimal activityMultiplier = NutritionConstants.DEFAULT_ACTIVITY_MULTIPLIER;

	@Builder.Default
	@Column(nullable = false)
	private boolean bodyFatAutoCalculationEnabled = false;

	@CreatedDate
	@Column(updatable = false, nullable = false)
	private Instant createdDate;

	@LastModifiedDate
	@Column(nullable = false)
	private Instant lastModifiedDate;

	@CreatedBy
	@Column(updatable = false)
	private UUID createdBy;

	@LastModifiedBy
	private UUID lastModifiedBy;
}