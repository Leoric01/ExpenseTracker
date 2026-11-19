package org.leoric.expensetracker.vacation.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vacation_photo", indexes = {@Index(columnList = "vacation_id"), @Index(columnList = "visit_id")})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor
public class VacationPhoto {
	@Id @UuidGenerator @Column(columnDefinition = "BINARY(16)")
	private UUID id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "vacation_id", nullable = false)
	private Vacation vacation;
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "visit_id")
	private VacationVisit visit;
	@Column(nullable = false, length = 2000)
	private String imageUrl;
	@CreatedDate @Column(updatable = false)
	private Instant createdDate;
	@CreatedBy @Column(updatable = false)
	private UUID createdBy;
}
