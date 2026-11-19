package org.leoric.expensetracker.restaurant.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "restaurant_visit", indexes = @Index(columnList = "restaurant_id"))
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor
public class RestaurantVisit {
	@Id @UuidGenerator(style = UuidGenerator.Style.AUTO)
	@Column(columnDefinition = "BINARY(16)")
	private UUID id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "restaurant_id", nullable = false)
	private Restaurant restaurant;
	private LocalDate visitedOn;
	@Column(length = 80)
	private String serviceName;
	@Column(length = 2000)
	private String note;
	private Integer rating;
	@Column(nullable = false, length = 20)
	private String currencyCode;
	@Column(nullable = false)
	private int currencyScale;
	private Long totalMinor;
	private Long deliveryMinor;
	@CreatedDate @Column(nullable = false, updatable = false)
	private Instant createdDate;
	@LastModifiedDate @Column(nullable = false)
	private Instant lastModifiedDate;
	@CreatedBy @Column(updatable = false)
	private UUID createdBy;
	@LastModifiedBy
	private UUID lastModifiedBy;
}
