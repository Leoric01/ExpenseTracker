package org.leoric.expensetracker.wallet.reworked;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
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
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "holding", uniqueConstraints = {@UniqueConstraint(columnNames = {"account_id", "asset_id"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Holding {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.AUTO)
	@Column(columnDefinition = "BINARY(16)")
	private UUID id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "account_id", nullable = false)
	private Account account;

	@ManyToOne(optional = false)
	@JoinColumn(name = "asset_id", nullable = false)
	private Asset asset;

	@Builder.Default
	@Column(nullable = false)
	private long initialAmount = 0;

	@Builder.Default
	@Column(nullable = false)
	private long currentAmount = 0;

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