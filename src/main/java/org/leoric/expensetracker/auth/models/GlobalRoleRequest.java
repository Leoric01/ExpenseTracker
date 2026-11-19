package org.leoric.expensetracker.auth.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.leoric.expensetracker.expensetracker.models.constants.ExpenseTrackerAccessRequestStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "global_role_requests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlobalRoleRequest {
	@Id
	@UuidGenerator
	@Column(columnDefinition = "BINARY(16)")
	private UUID id;
	@ManyToOne(optional = false)
	private User user;
	@Column(nullable = false)
	private String roleName;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ExpenseTrackerAccessRequestStatus status;
	@Column(nullable = false)
	private Instant requestDate;
	private Instant decisionDate;
	@ManyToOne
	private User decidedBy;
}
