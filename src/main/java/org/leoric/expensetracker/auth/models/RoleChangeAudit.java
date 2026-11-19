package org.leoric.expensetracker.auth.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "role_change_audit")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleChangeAudit {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(nullable = false, columnDefinition = "BINARY(16)")
	private UUID actorId;
	@Column(nullable = false, columnDefinition = "BINARY(16)")
	private UUID targetId;
	@Column(columnDefinition = "BINARY(16)")
	private UUID trackerId;
	@Column(nullable = false)
	private String roleName;
	@Column(nullable = false)
	private String action;
	@Column(nullable = false)
	private Instant changedAt;
}
