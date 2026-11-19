package org.leoric.expensetracker.vacation.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vacation_place", indexes = @Index(columnList = "expense_tracker_id"))
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor
public class VacationPlace {
	@Id @UuidGenerator @Column(columnDefinition = "BINARY(16)")
	private UUID id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "expense_tracker_id", nullable = false)
	private ExpenseTracker expenseTracker;
	@Column(nullable = false, length = 180)
	private String name;
	@Column(nullable = false, length = 80)
	private String kind;
	@Column(length = 120)
	private String country;
	@Column(length = 180)
	private String location;
	@Column(length = 4000)
	private String note;
	@Column(nullable = false, length = 20)
	private String recommendation = "NORMAL";
	@Version private long version;
	@CreatedDate @Column(nullable = false, updatable = false)
	private Instant createdDate;
	@LastModifiedDate @Column(nullable = false)
	private Instant lastModifiedDate;
	@CreatedBy @Column(updatable = false)
	private UUID createdBy;
	@LastModifiedBy private UUID lastModifiedBy;
}
