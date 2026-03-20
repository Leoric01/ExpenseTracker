package org.leoric.expensetracker.auth.models;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class User implements UserDetails, Principal {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.AUTO)
	@Column(columnDefinition = "BINARY(16)")
	private UUID id;

	@CreatedDate
	@Column(updatable = false, nullable = false)
	private Instant createdDate;

	@LastModifiedDate
	private Instant lastModifiedDate;

	private String firstName;
	private String lastName;

	@Column(unique = true, nullable = false)
	private String email;

	private String password;

	private boolean accountLocked;
	private boolean enabled;

	@ManyToMany(fetch = FetchType.EAGER)
	@Builder.Default
	private List<Role> roles = new ArrayList<>();

	@ManyToMany(fetch = FetchType.EAGER)
	@JoinTable(
			name = "user_expense_tracker",
			joinColumns = @JoinColumn(name = "user_id"),
			inverseJoinColumns = @JoinColumn(name = "expense_tracker_id")
	)
	@Builder.Default
	private List<ExpenseTracker> expenseTrackers = new ArrayList<>();

	@Builder.Default
	@OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
	private List<UserExpenseTrackerRole> userExpenseTrackerRoles = new ArrayList<>();

	@Builder.Default
	@OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<NavbarFavourite> navbarFavourites = new ArrayList<>();

	@Override
	public String getName() {
		return this.email;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return this.roles
				.stream()
				.map(role -> new SimpleGrantedAuthority(role.getName()))
				.collect(Collectors.toList());
	}

	@Override
	public String getPassword() {
		return this.password;
	}

	@Override
	public String getUsername() {
		return this.email;
	}

	@Override
	public boolean isAccountNonLocked() {
		return !accountLocked;
	}

	@Override
	public boolean isEnabled() {
		return this.enabled;
	}

	public String getFullname() {
		return this.firstName + " " + this.lastName;
	}

	public boolean hasRole(String role) {
		return this.getAuthorities().stream()
				.anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equalsIgnoreCase(role));
	}

	public boolean hasGlobalRole(String roleName) {
		return roles.stream().anyMatch(r -> r.getName().equalsIgnoreCase(roleName));
	}

	public boolean hasRoleOnExpenseTracker(String roleName, UUID expenseTrackerId) {
		return userExpenseTrackerRoles.stream()
				.anyMatch(ubr -> ubr.getExpenseTracker().getId().equals(expenseTrackerId) &&
						ubr.getRole().getName().equalsIgnoreCase(roleName));
	}
}