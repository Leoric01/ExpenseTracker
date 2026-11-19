package org.leoric.expensetracker.vacation.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "vacation_expense", indexes = @Index(columnList = "vacation_id"))
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor
public class VacationExpense {
	@Id @UuidGenerator @Column(columnDefinition = "BINARY(16)")
	private UUID id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "vacation_id", nullable = false)
	private Vacation vacation;
	@Column(nullable = false, length = 180)
	private String name;
	@Column(length = 80)
	private String kind;
	@Column(nullable = false)
	private Long amountMinor;
	@Column(nullable = false, length = 20)
	private String currencyCode;
	@Column(nullable = false)
	private int currencyScale;
	private LocalDate expenseDate;
	@Column(length = 2000)
	private String note;
	@Version private long version;
	@CreatedDate @Column(nullable = false, updatable = false)
	private Instant createdDate;
	@LastModifiedDate @Column(nullable = false)
	private Instant lastModifiedDate;
	@CreatedBy @Column(updatable = false)
	private UUID createdBy;
	@LastModifiedBy private UUID lastModifiedBy;
}
