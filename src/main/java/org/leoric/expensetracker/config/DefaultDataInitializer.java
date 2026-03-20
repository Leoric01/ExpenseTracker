package org.leoric.expensetracker.config;

import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.ExpenseTrackerApplication;
import org.leoric.expensetracker.auth.models.Role;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.auth.repositories.RoleRepository;
import org.leoric.expensetracker.auth.repositories.UserRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DefaultDataInitializer {

    private static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DefaultAdminProperties defaultAdminProperties;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initializeDefaultData() {
        runWithSystemAuditor(() -> {
            seedRoles();
            seedAdminUser();
        });
    }

    private void seedRoles() {
        List.of(
                ExpenseTrackerApplication.ADMIN,
                ExpenseTrackerApplication.USER,
                ExpenseTrackerApplication.EXPENSE_TRACKER_OWNER,
                ExpenseTrackerApplication.EXPENSE_TRACKER_MEMBER
        ).forEach(this::ensureRole);
    }

    private void ensureRole(String roleName) {
        roleRepository.findByName(roleName)
                .orElseGet(() -> roleRepository.save(Role.builder().name(roleName).build()));
    }

    private void seedAdminUser() {
        if (userRepository.existsByEmail(defaultAdminProperties.email())) {
            return;
        }

        Role adminRole = roleRepository.findByName(ExpenseTrackerApplication.ADMIN)
                .orElseThrow(() -> new IllegalStateException("ADMIN role should exist when seeding data"));

        User admin = User.builder()
                .firstName(defaultAdminProperties.firstName())
                .lastName(defaultAdminProperties.lastName())
                .email(defaultAdminProperties.email())
                .password(passwordEncoder.encode(defaultAdminProperties.password()))
                .enabled(true)
                .accountLocked(false)
                .roles(List.of(adminRole))
                .build();

        userRepository.save(admin);
    }

    private void runWithSystemAuditor(Runnable action) {
        var previous = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(createSystemUser(), null, List.of())
        );

        try {
            action.run();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }

    private User createSystemUser() {
        return User.builder()
                .id(SYSTEM_USER_ID)
                .email("system@initializer.local")
                .firstName("System")
                .lastName("Seeder")
                .password("")
                .enabled(true)
                .accountLocked(false)
                .roles(List.of())
                .build();
    }
}