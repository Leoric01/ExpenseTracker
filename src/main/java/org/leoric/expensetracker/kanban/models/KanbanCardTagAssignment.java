package org.leoric.expensetracker.kanban.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kanban_card_tag_assignment",
		uniqueConstraints = {
				@UniqueConstraint(columnNames = {"kanban_card_id", "kanban_tag_id"})
		},
		indexes = {
				@Index(name = "idx_kanban_card_tag_assignment_card", columnList = "kanban_card_id"),
				@Index(name = "idx_kanban_card_tag_assignment_tag", columnList = "kanban_tag_id")
		}
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class KanbanCardTagAssignment {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.AUTO)
	@Column(columnDefinition = "BINARY(16)")
	private UUID id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "kanban_card_id", nullable = false)
	private KanbanCard card;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "kanban_tag_id", nullable = false)
	private KanbanTag tag;

	@CreatedDate
	@Column(updatable = false, nullable = false)
	private Instant createdDate;

	@CreatedBy
	@Column(updatable = false)
	private UUID createdBy;
}