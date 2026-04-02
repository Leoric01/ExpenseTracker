package org.leoric.expensetracker.wallet.reworked;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.leoric.expensetracker.wallet.reworked.constants.AccountType;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"institution_id", "name"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Account {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.AUTO)
	@Column(columnDefinition = "BINARY(16)")
	private UUID id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "institution_id", nullable = false)
	private Institution institution;

	@Column(nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private AccountType accountType;

	private String description;

	private String externalRef;

	private String iconUrl;

	@Column(length = 7)
	private String iconColor;

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