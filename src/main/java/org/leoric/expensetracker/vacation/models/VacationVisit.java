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
import java.util.*;

@Entity
@Table(name = "vacation_visit", indexes = {@Index(columnList = "vacation_id"), @Index(columnList = "place_id")})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor
public class VacationVisit {
	@Id @UuidGenerator @Column(columnDefinition = "BINARY(16)")
	private UUID id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "vacation_id", nullable = false)
	private Vacation vacation;
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "place_id")
	private VacationPlace place;
	@Column(nullable = false, length = 180)
	private String nameSnapshot;
	@Column(nullable = false, length = 80)
	private String kindSnapshot;
	private LocalDate startDate;
	private LocalDate endDate;
	private Integer rating;
	@Column(length = 4000)
	private String note;
	@Column(length = 2000)
	private String positives;
	@Column(length = 2000)
	private String negatives;
	@ElementCollection
	@CollectionTable(name = "vacation_visit_rating", joinColumns = @JoinColumn(name = "visit_id"))
	@MapKeyColumn(name = "category", length = 80)
	@Column(name = "score", nullable = false)
	private Map<String, Integer> ratings = new LinkedHashMap<>();
	@Version private long version;
	@CreatedDate @Column(nullable = false, updatable = false)
	private Instant createdDate;
	@LastModifiedDate @Column(nullable = false)
	private Instant lastModifiedDate;
	@CreatedBy @Column(updatable = false)
	private UUID createdBy;
	@LastModifiedBy private UUID lastModifiedBy;
}
