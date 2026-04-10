package org.leoric.expensetracker.habit.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
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
import org.leoric.expensetracker.habit.models.constants.DayBlock;

import java.time.DayOfWeek;
import java.util.UUID;

@Entity
@Table(name = "habit_schedule_slot", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"habit_id", "day_of_week", "day_block"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HabitScheduleSlot {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.AUTO)
	@Column(columnDefinition = "BINARY(16)")
	private UUID id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "habit_id", nullable = false)
	private Habit habit;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private DayOfWeek dayOfWeek;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private DayBlock dayBlock;

	@Builder.Default
	@Column(nullable = false)
	private Integer sortOrder = 0;
}