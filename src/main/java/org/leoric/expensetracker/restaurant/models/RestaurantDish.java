package org.leoric.expensetracker.restaurant.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "restaurant_dish", indexes = @Index(columnList = "restaurant_id"))
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor
public class RestaurantDish {
	@Id @UuidGenerator(style = UuidGenerator.Style.AUTO)
	@Column(columnDefinition = "BINARY(16)")
	private UUID id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "restaurant_id", nullable = false)
	private Restaurant restaurant;
	@Column(nullable = false, length = 180)
	private String name;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Recommendation recommendation = Recommendation.NORMAL;
	@CreatedDate @Column(updatable = false)
	private Instant createdDate;
	@LastModifiedDate
	private Instant lastModifiedDate;
	@CreatedBy @Column(updatable = false)
	private UUID createdBy;
	@LastModifiedBy
	private UUID lastModifiedBy;
}
