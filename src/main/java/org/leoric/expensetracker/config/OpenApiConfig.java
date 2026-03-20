package org.leoric.expensetracker.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@OpenAPIDefinition(
        info = @Info(
                title = "Expense Tracker API",
                version = "1.0",
                description = "Swagger dokumentace pro ExpenseTracker backend"
        )
)
@Configuration
public class OpenApiConfig {
}