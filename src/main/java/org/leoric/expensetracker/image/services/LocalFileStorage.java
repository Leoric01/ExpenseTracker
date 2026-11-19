package org.leoric.expensetracker.image.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Slf4j
public class LocalFileStorage {
	private static final Pattern FOLDER = Pattern.compile("expense-tracker/(accounts|institutions|categories|todo|kanban|attachments|workout|restaurants|vacations)");
	private static final Pattern FILE = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp|heic|heif|gif|avif|pdf)");
	private static final Map<String, String> EXTENSIONS = Map.of(
			"image/jpeg", "jpg", "image/png", "png", "image/webp", "webp", "image/heic", "heic", "image/heif", "heif",
			"image/gif", "gif", "image/avif", "avif", "application/pdf", "pdf");
	private static final Map<String, String> CONTENT_TYPES = Map.of(
			"jpg", "image/jpeg", "png", "image/png", "webp", "image/webp", "heic", "image/heic", "heif", "image/heif",
			"gif", "image/gif", "avif", "image/avif", "pdf", "application/pdf");
	private final Path root;

	public LocalFileStorage(@Value("${app.image-storage.local-directory:images}") String directory) {
		if (directory == null || directory.isBlank()) {
			throw new IllegalArgumentException("Local image storage directory must not be blank");
		}
		this.root = Path.of(directory).toAbsolutePath().normalize();
	}

	public String save(MultipartFile file, String folder, boolean imagesOnly) {
		if (file == null || file.isEmpty()) throw new OperationNotPermittedException("File is required");
		if (folder == null || !FOLDER.matcher(folder).matches()) throw new IllegalArgumentException("Invalid storage folder");
		String extension = EXTENSIONS.get(file.getContentType());
		if (extension == null || (imagesOnly && extension.equals("pdf"))) {
			throw new OperationNotPermittedException("Unsupported file type: " + file.getContentType());
		}
		try {
			try (InputStream input = file.getInputStream()) {
				if (!matchesType(extension, input.readNBytes(16))) {
					throw new OperationNotPermittedException("File content does not match its declared type");
				}
			}
			Files.createDirectories(root);
			Path directory = root.resolve(folder);
			Files.createDirectories(directory);
			if (!directory.toRealPath().startsWith(root.toRealPath())) {
				throw new IllegalStateException("Local storage folder points outside its root");
			}
			String name = UUID.randomUUID() + "." + extension;
			Path destination = directory.resolve(name);
			boolean created = false;
			try (InputStream input = file.getInputStream()) {
				var output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
				created = true;
				try (output) {
					input.transferTo(output);
				}
			} catch (IOException e) {
				if (created) {
					try {
						Files.deleteIfExists(destination);
					} catch (IOException cleanupError) {
						e.addSuppressed(cleanupError);
					}
				}
				throw e;
			}
			return "/images/" + folder + "/" + name;
		} catch (IOException e) {
			throw new IllegalStateException("Failed to save file in local storage", e);
		}
	}

	public StoredFile read(String namespace, String group, String name) {
		Path path = resolve(namespace + "/" + group, name);
		try {
			if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
					|| !path.toRealPath().startsWith(root.toRealPath())) {
				throw new EntityNotFoundException("File not found");
			}
		} catch (IOException e) {
			throw new IllegalStateException("Failed to read local file", e);
		}
		return new StoredFile(new FileSystemResource(path), CONTENT_TYPES.get(name.substring(name.lastIndexOf('.') + 1)));
	}

	public boolean deleteByUrl(String url) {
		if (url == null || !url.startsWith("/images/")) return false;
		String[] parts = url.substring("/images/".length()).split("/", -1);
		if (parts.length != 3) return false;
		try {
			Path path = resolve(parts[0] + "/" + parts[1], parts[2]);
			if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return true;
			if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
					|| !path.toRealPath().startsWith(root.toRealPath())) {
				log.warn("Refusing to delete invalid local file");
				return false;
			}
			Files.delete(path);
			return true;
		} catch (IllegalArgumentException | IOException e) {
			log.warn("Failed to delete local file", e);
			return false;
		}
	}

	private Path resolve(String folder, String name) {
		if (!FOLDER.matcher(folder).matches() || name == null || !FILE.matcher(name).matches()) {
			throw new IllegalArgumentException("Invalid local file path");
		}
		return root.resolve(folder).resolve(name);
	}

	private boolean matchesType(String extension, byte[] header) {
		return switch (extension) {
			case "jpg" -> startsWith(header, 0xff, 0xd8, 0xff);
			case "png" -> startsWith(header, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a);
			case "webp" -> startsWith(header, 0x52, 0x49, 0x46, 0x46) && at(header, 8, "WEBP");
			case "gif" -> at(header, 0, "GIF87a") || at(header, 0, "GIF89a");
			case "heic", "heif" -> at(header, 4, "ftyp") && (at(header, 8, "heic") || at(header, 8, "heix")
					|| at(header, 8, "hevc") || at(header, 8, "hevx") || at(header, 8, "mif1") || at(header, 8, "msf1"));
			case "avif" -> at(header, 4, "ftyp") && (at(header, 8, "avif") || at(header, 8, "avis"));
			case "pdf" -> at(header, 0, "%PDF-");
			default -> false;
		};
	}

	private boolean startsWith(byte[] bytes, int... prefix) {
		if (bytes.length < prefix.length) return false;
		for (int i = 0; i < prefix.length; i++) if ((bytes[i] & 0xff) != prefix[i]) return false;
		return true;
	}

	private boolean at(byte[] bytes, int offset, String value) {
		if (bytes.length < offset + value.length()) return false;
		for (int i = 0; i < value.length(); i++) if (bytes[offset + i] != value.charAt(i)) return false;
		return true;
	}

	public record StoredFile(Resource resource, String contentType) {}
}
