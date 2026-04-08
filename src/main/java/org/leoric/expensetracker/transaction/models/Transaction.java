package org.leoric.expensetracker.transaction.models;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.leoric.expensetracker.category.models.Category;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.transaction.models.constants.BalanceAdjustmentDirection;
import org.leoric.expensetracker.transaction.models.constants.TransactionStatus;
import org.leoric.expensetracker.transaction.models.constants.TransactionType;
import org.leoric.expensetracker.holding.models.Holding;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "transaction", indexes = {
		@Index(columnList = "expense_tracker_id, transaction_date"),
		@Index(columnList = "expense_tracker_id, holding_id, transaction_date"),
		@Index(columnList = "expense_tracker_id, category_id, transaction_date"),
		@Index(columnList = "expense_tracker_id, transaction_type, transaction_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Transaction {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.AUTO)
	@Column(columnDefinition = "BINARY(16)")
	private UUID id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "expense_tracker_id", nullable = false)
	private ExpenseTracker expenseTracker;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TransactionType transactionType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TransactionStatus status;

	@ManyToOne(fetch = FetchType.LAZY)
	private Holding holding;

	@ManyToOne(fetch = FetchType.LAZY)
	private Holding sourceHolding;

	@ManyToOne(fetch = FetchType.LAZY)
	private Holding targetHolding;

	@ManyToOne(fetch = FetchType.LAZY)
	private Category category;

	@Column(nullable = false)
	private long amount;

	@Column(nullable = false, length = 3)
	private String currencyCode;

	@Column(precision = 18, scale = 8)
	private BigDecimal exchangeRate;

	@Builder.Default
	@Column(nullable = false)
	private long feeAmount = 0;

	private Long settledAmount;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private BalanceAdjustmentDirection balanceAdjustmentDirection;

	@Column(nullable = false)
	private Instant transactionDate;

	private String description;

	@Column(columnDefinition = "TEXT")
	private String note;

	private String externalRef;

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

	@Builder.Default
	@OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<TransactionAttachment> attachments = new ArrayList<>();
}