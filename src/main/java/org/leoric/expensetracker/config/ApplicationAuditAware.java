package org.leoric.expensetracker.config;

import lombok.NonNull;
import org.leoric.expensetracker.auth.models.User;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class ApplicationAuditAware implements AuditorAware<UUID> {

	@Override
	@NonNull
	public Optional<UUID> getCurrentAuditor() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
			return Optional.empty();
		}
		User userPrincipal = (User) authentication.getPrincipal();
		return Optional.ofNullable(Objects.requireNonNull(userPrincipal).getId());
	}
}