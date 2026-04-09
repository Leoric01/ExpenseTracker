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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.food.models.constants.BodyFatSource;
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
@Table(name = "daily_body_measurement_log", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"expense_tracker_id", "log_date"})
}, indexes = {
		@Index(name = "idx_daily_body_measurement_goal_plan", columnList = "goal_plan_id"),
		@Index(name = "idx_daily_body_measurement_date", columnList = "expense_tracker_id, log_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class DailyBodyMeasurementLog {

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

	@Column(name = "log_date", nullable = false)
	private LocalDate logDate;

	@Column(precision = 6, scale = 2)
	private BigDecimal waistCm;

	@Column(precision = 6, scale = 2)
	private BigDecimal neckCm;

	@Column(precision = 6, scale = 2)
	private BigDecimal hipCm;

	@Column(precision = 5, scale = 2)
	private BigDecimal calculatedBodyFatPercent;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private BodyFatSource bodyFatSource;

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