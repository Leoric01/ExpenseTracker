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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kanban_card_stage_history", indexes = {
		@Index(name = "idx_kanban_card_stage_history_card_changed", columnList = "kanban_card_id, changed_date"),
		@Index(name = "idx_kanban_card_stage_history_to_stage", columnList = "to_stage_id"),
		@Index(name = "idx_kanban_card_stage_history_from_stage", columnList = "from_stage_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class KanbanCardStageHistory {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.AUTO)
	@Column(columnDefinition = "BINARY(16)")
	private UUID id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "kanban_card_id", nullable = false)
	private KanbanCard card;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "from_stage_id")
	private KanbanStage fromStage;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "to_stage_id", nullable = false)
	private KanbanStage toStage;

	@Column(nullable = false)
	private Instant changedDate;

	@CreatedBy
	@Column(updatable = false)
	private UUID createdBy;
}