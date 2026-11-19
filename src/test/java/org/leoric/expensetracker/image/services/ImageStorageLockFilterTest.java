package org.leoric.expensetracker.image.services;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ImageStorageLockFilterTest {
	@Test
	void rejectsWritesDuringMigrationButAllowsReads() throws Exception {
		var filter = new ImageStorageLockFilter();
		var started = new CountDownLatch(1);
		var finish = new CountDownLatch(1);
		try (var executor = Executors.newSingleThreadExecutor()) {
			var migration = executor.submit(() -> {
				var request = new MockHttpServletRequest("PUT", "/profile/image-storage");
				request.setServletPath("/profile/image-storage");
				filter.doFilter(request, new MockHttpServletResponse(), (_, _) -> {
					started.countDown();
					try {
						if (!finish.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out");
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						throw new IllegalStateException(e);
					}
				});
				return null;
			});
			try {
				assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
				var writeResponse = new MockHttpServletResponse();
				filter.doFilter(new MockHttpServletRequest("POST", "/api/todo"), writeResponse,
						(_, _) -> { throw new AssertionError("Write should have been rejected"); });
				assertThat(writeResponse.getStatus()).isEqualTo(503);
				var readResponse = new MockHttpServletResponse();
				filter.doFilter(new MockHttpServletRequest("GET", "/api/todo"), readResponse,
						(_, _) -> readResponse.setStatus(204));
				assertThat(readResponse.getStatus()).isEqualTo(204);
			} finally {
				finish.countDown();
			}
			migration.get(5, TimeUnit.SECONDS);
		}
	}
}
