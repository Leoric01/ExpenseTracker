package org.leoric.expensetracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.default-admin")
public record DefaultAdminProperties(
        String email,
        String password,
        String firstName,
        String lastName
) {
    public DefaultAdminProperties {
        email = email != null ? email : "admin@admin.com";
        password = password != null ? password : "admin";
        firstName = firstName != null ? firstName : "System";
        lastName = lastName != null ? lastName : "Administrator";
    }
}