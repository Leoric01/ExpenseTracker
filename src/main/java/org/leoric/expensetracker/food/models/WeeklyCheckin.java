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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "weekly_checkin", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"goal_plan_id", "week_index"})
}, indexes = {
		@Index(name = "idx_weekly_checkin_expense_tracker", columnList = "expense_tracker_id"),
		@Index(name = "idx_weekly_checkin_week_start", columnList = "goal_plan_id, week_start_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class WeeklyCheckin {

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

	@Column(name = "week_index", nullable = false)
	private Integer weekIndex;

	@Column(name = "week_start_date", nullable = false)
	private LocalDate weekStartDate;

	@Column(name = "week_end_date", nullable = false)
	private LocalDate weekEndDate;

	@Column(precision = 6, scale = 2)
	private BigDecimal avgWeightKg;

	@Column(precision = 8, scale = 2)
	private BigDecimal avgCaloriesKcal;

	@Column(precision = 5, scale = 2)
	private BigDecimal bodyFatPercent;

	@Column(precision = 7, scale = 2)
	private BigDecimal weightChangeFromStartKg;

	@Column(precision = 7, scale = 2)
	private BigDecimal weightChangeFromPreviousCheckinKg;

	@Column(precision = 8, scale = 2)
	private BigDecimal avgEstimatedTdeeKcal;

	@Column(nullable = false)
	private Integer daysWithWeight;

	@Column(nullable = false)
	private Integer daysWithCalories;

	@Column(nullable = false)
	private Integer daysWithBodyMeasurements;

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