package org.leoric.expensetracker.food.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.leoric.expensetracker.food.models.constants.BodyFatSource;
import org.leoric.expensetracker.food.models.constants.CarbStrategy;
import org.leoric.expensetracker.food.models.constants.FatStrategy;
import org.leoric.expensetracker.food.models.constants.GoalType;
import org.leoric.expensetracker.food.models.constants.ProteinStrategy;
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
@Table(name = "goal_plan", indexes = {
		@Index(name = "idx_goal_plan_expense_tracker", columnList = "expense_tracker_id"),
		@Index(name = "idx_goal_plan_active", columnList = "expense_tracker_id, active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class GoalPlan {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.AUTO)
	@Column(columnDefinition = "BINARY(16)")
	private UUID id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "expense_tracker_id", nullable = false)
	private ExpenseTracker expenseTracker;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "nutrition_profile_id", nullable = false)
	private NutritionProfile nutritionProfile;

	@Column(nullable = false, length = 120)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private GoalType goalType;

	@Column(nullable = false)
	private LocalDate startDate;

	private LocalDate endDate;

	@Column(nullable = false, precision = 6, scale = 2)
	private BigDecimal startWeightKg;

	@Column(precision = 5, scale = 2)
	private BigDecimal startBodyFatPercent;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private BodyFatSource startBodyFatSource;

	@Column(nullable = false, precision = 6, scale = 3)
	private BigDecimal targetWeeklyWeightChangeKg;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ProteinStrategy proteinStrategy;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private FatStrategy fatStrategy;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private CarbStrategy carbStrategy;

	@Builder.Default
	@Column(nullable = false)
	private boolean active = true;

	@Column(length = 500)
	private String notes;

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