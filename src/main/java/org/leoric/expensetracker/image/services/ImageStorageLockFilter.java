package org.leoric.expensetracker.image.services;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
public class ImageStorageLockFilter extends OncePerRequestFilter {
	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String method = request.getMethod();
		if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
			chain.doFilter(request, response);
			return;
		}
		boolean migration = "PUT".equals(method) && "/profile/image-storage".equals(request.getServletPath());
		var selected = migration ? lock.writeLock() : lock.readLock();
		boolean acquired;
		try {
			acquired = migration ? selected.tryLock(30, TimeUnit.SECONDS) : selected.tryLock(0, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			acquired = false;
		}
		if (!acquired) {
			response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
			response.setContentType("application/json");
			response.setHeader("Retry-After", "5");
			response.getWriter().write("{\"error\":\"Storage migration is in progress; try again later\"}");
			return;
		}
		try {
			chain.doFilter(request, response);
		} finally {
			selected.unlock();
		}
	}
}
