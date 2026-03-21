package org.leoric.expensetracker.wallet.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
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
import org.leoric.expensetracker.wallet.models.constants.WalletType;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallet", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"expense_tracker_id", "name"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Wallet {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.AUTO)
	@Column(columnDefinition = "BINARY(16)")
	private UUID id;

	@ManyToOne(optional = false)
	private ExpenseTracker expenseTracker;

	@Column(nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private WalletType walletType;

	@Column(nullable = false, length = 3)
	private String currencyCode;

	private String iconUrl;

	@Column(length = 7)
	private String iconColor;

	@Builder.Default
	@Column(nullable = false)
	private long initialBalance = 0;

	@Builder.Default
	@Column(nullable = false)
	private long currentBalance = 0;

	@Builder.Default
	@Column(nullable = false)
	private boolean active = true;

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