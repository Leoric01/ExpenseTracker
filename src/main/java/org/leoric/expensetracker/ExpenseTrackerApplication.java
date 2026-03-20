package org.leoric.expensetracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
@EnableAsync
public class ExpenseTrackerApplication {

	public final static String ADMIN = "ADMIN";
	public final static String USER = "USER";

	public final static String EXPENSE_TRACKER_OWNER = "EXPENSE_TRACKER_OWNER";
	public final static String EXPENSE_TRACKER_MEMBER = "EXPENSE_TRACKER_MEMBER";

	static void main(String[] args) {
		SpringApplication.run(ExpenseTrackerApplication.class, args);
	}
}