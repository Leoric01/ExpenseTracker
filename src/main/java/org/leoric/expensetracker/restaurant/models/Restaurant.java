package org.leoric.expensetracker.restaurant.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "restaurant", indexes = @Index(columnList = "expense_tracker_id"))
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor
public class Restaurant {
	@Id @UuidGenerator(style = UuidGenerator.Style.AUTO)
	@Column(columnDefinition = "BINARY(16)")
	private UUID id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "expense_tracker_id", nullable = false)
	private ExpenseTracker expenseTracker;
	@Column(nullable = false, length = 180)
	private String name;
	@Column(length = 2000)
	private String note;
	@Enumerated(EnumType.STRING)
	@Column(length = 10)
	private PriceLevel priceLevel;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Recommendation recommendation = Recommendation.NORMAL;
	@ElementCollection
	@CollectionTable(name = "restaurant_service_tag", joinColumns = @JoinColumn(name = "restaurant_id"))
	@Column(name = "service_name", nullable = false, length = 80)
	private List<String> services = new ArrayList<>();
	@CreatedDate @Column(nullable = false, updatable = false)
	private Instant createdDate;
	@LastModifiedDate @Column(nullable = false)
	private Instant lastModifiedDate;
	@CreatedBy @Column(updatable = false)
	private UUID createdBy;
	@LastModifiedBy
	private UUID lastModifiedBy;
}
