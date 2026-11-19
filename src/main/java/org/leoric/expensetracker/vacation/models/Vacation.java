package org.leoric.expensetracker.vacation.models;

import jakarta.persistence.*;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Entity
@Table(name = "vacation", indexes = @Index(columnList = "expense_tracker_id"))
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor
public class Vacation {
	@Id @UuidGenerator @Column(columnDefinition = "BINARY(16)")
	private UUID id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "expense_tracker_id", nullable = false)
	private ExpenseTracker expenseTracker;
	@Column(nullable = false, length = 180)
	private String name;
	@Column(nullable = false, length = 20)
	private String status;
	@Column(length = 80)
	private String type;
	@ElementCollection
	@CollectionTable(name = "vacation_focus", joinColumns = @JoinColumn(name = "vacation_id"))
	@Column(name = "focus", nullable = false, length = 80)
	private List<String> focuses = new ArrayList<>();
	@Column(name = "trip_year")
	private Integer year;
	private LocalDate startDate;
	private LocalDate endDate;
	@Column(nullable = false)
	private boolean approximate;
	@Column(length = 120)
	private String country;
	@Column(length = 180)
	private String location;
	@Column(length = 180)
	private String provider;
	@Column(length = 180)
	private String bookingMethod;
	private Integer people;
	private Long totalMinor;
	@Column(length = 20)
	private String currencyCode;
	private Integer currencyScale;
	private Integer rating;
	@Column(nullable = false, length = 20)
	private String recommendation = "NORMAL";
	@Column(length = 4000)
	private String note;
	@Column(length = 2000)
	private String positives;
	@Column(length = 2000)
	private String negatives;
	@ElementCollection
	@CollectionTable(name = "vacation_rating", joinColumns = @JoinColumn(name = "vacation_id"))
	@MapKeyColumn(name = "category", length = 80)
	@Column(name = "score", nullable = false)
	private Map<String, Integer> ratings = new LinkedHashMap<>();
	@Version
	private long version;
	@CreatedDate @Column(nullable = false, updatable = false)
	private Instant createdDate;
	@LastModifiedDate @Column(nullable = false)
	private Instant lastModifiedDate;
	@CreatedBy @Column(updatable = false)
	private UUID createdBy;
	@LastModifiedBy
	private UUID lastModifiedBy;
}
