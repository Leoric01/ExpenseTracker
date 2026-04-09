package org.leoric.expensetracker.food.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tdee_adjustment_event", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"weekly_checkin_id"})
}, indexes = {
		@Index(name = "idx_tdee_adjustment_goal_plan", columnList = "goal_plan_id"),
		@Index(name = "idx_tdee_adjustment_expense_tracker", columnList = "expense_tracker_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class TdeeAdjustmentEvent {

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

	@OneToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "weekly_checkin_id", nullable = false, unique = true)
	private WeeklyCheckin weeklyCheckin;

	@Column(nullable = false, precision = 8, scale = 2)
	private BigDecimal previousTargetCaloriesKcal;

	@Column(nullable = false, precision = 8, scale = 2)
	private BigDecimal newTargetCaloriesKcal;

	@Column(nullable = false, precision = 7, scale = 3)
	private BigDecimal observedWeightChangeKg;

	@Column(nullable = false, precision = 7, scale = 3)
	private BigDecimal expectedWeightChangeKg;

	@Column(nullable = false, precision = 8, scale = 2)
	private BigDecimal estimatedCalorieErrorKcal;

	@Column(nullable = false, precision = 8, scale = 2)
	private BigDecimal appliedAdjustmentKcal;

	@Column(nullable = false, length = 50)
	private String algorithmVersion;

	@Column(nullable = false, length = 50)
	private String reasonCode;

	@Column(length = 500)
	private String reasonDetail;

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