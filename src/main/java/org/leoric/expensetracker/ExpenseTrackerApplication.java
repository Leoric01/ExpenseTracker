package org.leoric.expensetracker;

import lombok.Setter;
import org.leoric.expensetracker.holding.dto.Experiment;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.core.userdetails.UserCache;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@SpringBootApplication
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
@EnableAsync
public class ExpenseTrackerApplication {

	public final static String ADMIN = "ADMIN";
	public final static String USER = "USER";

	public final static String EXPENSETRACKER_OWNER = "EXPENSETRACKER_OWNER";
	public final static String EXPENSETRACKER_MEMBER = "EXPENSETRACKER_MEMBER";

	@Setter
	public static class User {
		private int id;
		private String name;

		public User(String name) {
			this.name = name;
		}

		@Override
		public String toString() {
			return name;
		}
		@Override
		public boolean equals(Object obj) {
			if (obj instanceof User) {
				return name.equals(((User) obj).name);
			}
			return false;
		}
	}

	public static class UserCache {
		private static final List<User> users = new ArrayList<>();

		public static void getUsers() {
			System.out.println(users);
		}

		public static void add(User user) {
			users.add(user);
		}

		public static void clear() {
			users.clear();
		}
	}

	public static void main(String[] args) {
		SpringApplication.run(ExpenseTrackerApplication.class, args);
		String s = "hello";
		StringBuilder sb = new StringBuilder("hello");

		change(s, sb);
		System.out.println(s);
		System.out.println(sb);

	}
	public static void change(String s, StringBuilder sb) {
		s = s + " world";
		sb.append(" world");
	}
	public static int mergeString(String a, String b) {
		try {
			return 1;
		} finally {
			return 2;
		}
	}
}