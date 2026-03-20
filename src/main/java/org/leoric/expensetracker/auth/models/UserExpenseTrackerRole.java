package org.leoric.expensetracker.auth.models;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_expense_tracker_roles")
@EntityListeners(AuditingEntityListener.class)
public class UserExpenseTrackerRole {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne(optional = false)
	private User user;

	@ManyToOne(optional = false)
	private ExpenseTracker expenseTracker;

	@ManyToOne(optional = false)
	private Role role;
}