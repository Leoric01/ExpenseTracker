package org.leoric.expensetracker.image.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.leoric.expensetracker.image.models.ImageStorageProvider;
import org.leoric.expensetracker.image.models.ImageStorageSetting;
import org.leoric.expensetracker.image.repositories.ImageStorageSettingRepository;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LocalFileStorageTest {
	private static final byte[] PNG = Base64.getDecoder().decode(
			"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/l9sAAAAASUVORK5CYII=");
	@TempDir Path directory;

	@Test
	void localFilesCanBeReadAndDeletedAndAreNotNamedFromUserInput() throws Exception {
		var storage = new LocalFileStorage(directory.toString());
		String url = storage.save(new MockMultipartFile("file", "../../unsafe.png", "image/png", PNG),
				"expense-tracker/workout", true);
		assertThat(url).matches("/images/expense-tracker/workout/[0-9a-f-]{36}\\.png");
		assertThat(ImageUrlRules.isValid(url)).isTrue();
		assertThat(ImageUrlRules.isValid(url + "/../../secret")).isFalse();
		var result = storage.read("expense-tracker", "workout", url.substring(url.lastIndexOf('/') + 1));
		assertThat(result.contentType()).isEqualTo("image/png");
		assertThat(result.resource().getContentAsByteArray()).isEqualTo(PNG);
		assertThat(storage.deleteByUrl(url)).isTrue();
		assertThat(storage.deleteByUrl(url)).isTrue();
		try (var files = Files.list(directory.resolve("expense-tracker/workout"))) {
			assertThat(files.findAny()).isEmpty();
		}
	}

	@Test
	void removesPartialFileWhenUploadStreamFails() throws Exception {
		var storage = new LocalFileStorage(directory.toString());
		MultipartFile file = mock(MultipartFile.class);
		when(file.isEmpty()).thenReturn(false);
		when(file.getContentType()).thenReturn("image/png");
		when(file.getInputStream()).thenReturn(new ByteArrayInputStream(PNG), new InputStream() {
			private boolean first = true;
			@Override public int read() throws IOException {
				if (first) { first = false; return 0x89; }
				throw new IOException("Upload interrupted");
			}
		});
		assertThatThrownBy(() -> storage.save(file, "expense-tracker/workout", true))
				.isInstanceOf(IllegalStateException.class);
		try (var files = Files.list(directory.resolve("expense-tracker/workout"))) {
			assertThat(files.findAny()).isEmpty();
		}
	}

	@Test
	void pdfAttachmentsAreAllowedButWorkoutImagesAreNot() {
		var storage = new LocalFileStorage(directory.toString());
		var pdf = new MockMultipartFile("file", "receipt.pdf", "application/pdf", "%PDF-1.4".getBytes());
		String url = storage.save(pdf, "expense-tracker/attachments", false);
		assertThat(storage.read("expense-tracker", "attachments", url.substring(url.lastIndexOf('/') + 1)).contentType())
				.isEqualTo("application/pdf");
		assertThatThrownBy(() -> storage.save(pdf, "expense-tracker/workout", true))
				.isInstanceOf(OperationNotPermittedException.class);
	}

	@Test
	void acceptsHeifAttachments() {
		var storage = new LocalFileStorage(directory.toString());
		var file = new MockMultipartFile("file", "photo.heif", "image/heif",
				new byte[]{0, 0, 0, 16, 'f', 't', 'y', 'p', 'm', 'i', 'f', '1', 0, 0, 0, 0});
		String url = storage.save(file, "expense-tracker/attachments", false);
		assertThat(url).endsWith(".heif");
		assertThat(storage.read("expense-tracker", "attachments", url.substring(url.lastIndexOf('/') + 1)).contentType())
				.isEqualTo("image/heif");
	}

	@Test
	void refusesSymlinksOutsideStorageRoot() throws Exception {
		Path root = directory.resolve("root");
		Path outside = Files.createDirectory(directory.resolve("outside"));
		Files.createDirectories(root.resolve("expense-tracker"));
		Files.createSymbolicLink(root.resolve("expense-tracker/workout"), outside);
		var storage = new LocalFileStorage(root.toString());
		var file = new MockMultipartFile("file", "x.png", "image/png", PNG);
		assertThatThrownBy(() -> storage.save(file, "expense-tracker/workout", true))
				.isInstanceOf(IllegalStateException.class);
		String name = UUID.randomUUID() + ".png";
		Files.write(outside.resolve(name), PNG);
		assertThatThrownBy(() -> storage.read("expense-tracker", "workout", name))
				.isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
		assertThat(storage.deleteByUrl("/images/expense-tracker/workout/" + name)).isFalse();
		assertThat(Files.exists(outside.resolve(name))).isTrue();
	}

	@Test
	void rejectsMalformedFilesAndUnsafePaths() {
		var storage = new LocalFileStorage(directory.toString());
		assertThatThrownBy(() -> storage.save(new MockMultipartFile("file", "x.png", "image/png", "not a png".getBytes()),
				"expense-tracker/workout", true)).isInstanceOf(OperationNotPermittedException.class);
		assertThatThrownBy(() -> storage.save(new MockMultipartFile("file", "x.png", "image/png", PNG),
				"../outside", true)).isInstanceOf(IllegalArgumentException.class);
		assertThat(storage.deleteByUrl("/images/expense-tracker/workout/../../secret")).isFalse();
		assertThatThrownBy(() -> storage.read("expense-tracker", "workout", "../secret"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void localModeCanStillDeletePreviouslyUploadedCloudinaryImages() throws Exception {
		Cloudinary cloudinary = mock(Cloudinary.class);
		Uploader uploader = mock(Uploader.class);
		when(cloudinary.uploader()).thenReturn(uploader);
		when(uploader.destroy(eq("expense-tracker/todo/example"), any(Map.class))).thenReturn(Map.of("result", "ok"));
		var service = new ImageServiceImpl(cloudinary, new LocalFileStorage(directory.toString()), mock(ImageStorageSettingRepository.class));
		assertThat(service.deleteImageByUrl(
				"https://res.cloudinary.com/demo/image/upload/v123/expense-tracker/todo/example.png")).isTrue();
		verify(uploader).destroy(eq("expense-tracker/todo/example"), any(Map.class));
	}

	@Test
	void cloudinaryModeStillUploadsAndCanDeleteExistingLocalImages() throws Exception {
		Cloudinary cloudinary = mock(Cloudinary.class);
		Uploader uploader = mock(Uploader.class);
		when(cloudinary.uploader()).thenReturn(uploader);
		when(uploader.upload(any(byte[].class), any(Map.class)))
				.thenReturn(Map.of("secure_url", "https://res.cloudinary.com/demo/image/upload/example.png"));
		var storage = new LocalFileStorage(directory.toString());
		var file = new MockMultipartFile("file", "x.png", "image/png", PNG);
		String localUrl = storage.save(file, "expense-tracker/workout", true);
		var settings = mock(ImageStorageSettingRepository.class);
		when(settings.findById(1)).thenReturn(Optional.of(new ImageStorageSetting(ImageStorageProvider.CLOUDINARY)));
		var service = new ImageServiceImpl(cloudinary, storage, settings);
		assertThat(service.uploadImage(file, "expense-tracker/workout"))
				.isEqualTo("https://res.cloudinary.com/demo/image/upload/example.png");
		assertThat(service.deleteImageByUrl(localUrl)).isTrue();
	}

}
