package org.leoric.expensetracker.food.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "nutrition_target", indexes = {
		@Index(name = "idx_nutrition_target_goal_plan", columnList = "goal_plan_id"),
		@Index(name = "idx_nutrition_target_effective_from", columnList = "goal_plan_id, effective_from")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class NutritionTarget {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.AUTO)
	@Column(columnDefinition = "BINARY(16)")
	private UUID id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "expense_tracker_id", nullable = false)
	private ExpenseTracker expenseTracker;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "goal_plan_id", nullable = false)
	private GoalPlan goalPlan;

	@Column(name = "effective_from", nullable = false)
	private LocalDate effectiveFrom;

	@Column(name = "effective_to")
	private LocalDate effectiveTo;

	@Column(nullable = false, precision = 8, scale = 2)
	private BigDecimal baselineTdeeKcal;

	@Column(nullable = false, precision = 8, scale = 2)
	private BigDecimal calorieAdjustmentKcal;

	@Column(nullable = false, precision = 8, scale = 2)
	private BigDecimal targetCaloriesKcal;

	@Column(nullable = false, precision = 7, scale = 2)
	private BigDecimal targetProteinG;

	@Column(nullable = false, precision = 7, scale = 2)
	private BigDecimal targetFatG;

	@Column(nullable = false, precision = 7, scale = 2)
	private BigDecimal targetCarbsG;

	@Column(nullable = false, length = 50)
	private String algorithmVersion;

	@Column(nullable = false, length = 50)
	private String reasonCode;

	@Column(length = 500)
	private String reasonDetail;

	@Builder.Default
	@Column(nullable = false)
	private boolean manualOverride = false;

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