package org.leoric.expensetracker.image.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.config.CloudinaryConfig.CloudinaryProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CloudinaryFileAccess {
	private static final int MAX_BYTES = 10 * 1024 * 1024;
	private final Cloudinary cloudinary;
	private final CloudinaryProperties properties;

	public boolean isConfigured() {
		return present(properties.getCloudName(), "your-cloud-name")
				&& present(properties.getApiKey(), "your-api-key")
				&& present(properties.getApiSecret(), "your-api-secret");
	}

	private boolean present(String value, String placeholder) {
		return value != null && !value.isBlank() && !value.equals(placeholder);
	}

	public StoredFileData download(String url) {
		parse(url);
		HttpURLConnection connection = null;
		try {
			connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
			connection.setConnectTimeout(15_000);
			connection.setReadTimeout(60_000);
			connection.setInstanceFollowRedirects(false);
			int status = connection.getResponseCode();
			if (status != 200) throw new IllegalStateException("Cloudinary returned HTTP " + status);
			String header = connection.getContentType();
			if (header == null) throw new IllegalStateException("Cloudinary returned no content type");
			String contentType = header.split(";", 2)[0].trim();
			try (var stream = connection.getInputStream()) {
				byte[] data = stream.readNBytes(MAX_BYTES + 1);
				if (data.length > MAX_BYTES) throw new IllegalStateException("File exceeds 10 MB");
				return new StoredFileData(contentType, data);
			}
		} catch (IOException e) {
			throw new IllegalStateException("Cloudinary download failed", e);
		} finally {
			if (connection != null) connection.disconnect();
		}
	}

	public boolean delete(String url) {
		CloudinaryPath path = parse(url);
		try {
			Map<?, ?> result = cloudinary.uploader().destroy(path.publicId(),
					ObjectUtils.asMap("resource_type", path.resourceType(), "invalidate", true));
			String status = String.valueOf(result.get("result"));
			return "ok".equalsIgnoreCase(status) || "not found".equalsIgnoreCase(status);
		} catch (IOException e) {
			throw new IllegalStateException("Cloudinary deletion failed", e);
		}
	}

	public boolean sameFile(String first, String second) {
		return parse(first).equals(parse(second));
	}

	private CloudinaryPath parse(String url) {
		if (!isConfigured()) throw new IllegalStateException("Cloudinary credentials are not configured");
		URI uri = URI.create(url);
		if (!"https".equals(uri.getScheme()) || !"res.cloudinary.com".equals(uri.getHost())
				|| uri.getPort() != -1 || uri.getRawQuery() != null || uri.getRawFragment() != null
				|| uri.getRawUserInfo() != null) {
			throw new IllegalArgumentException("Unsupported Cloudinary URL");
		}
		String path = uri.getRawPath();
		var matcher = Pattern.compile("^/" + Pattern.quote(properties.getCloudName())
				+ "/(image|raw|video)/upload/(?:v[0-9]+/)?(expense-tracker/(?:accounts|institutions|categories|todo|kanban|attachments|workout|restaurants|vacations)/[A-Za-z0-9_.-]+)$")
				.matcher(path);
		if (!matcher.matches()) throw new IllegalArgumentException("Unsupported Cloudinary file path");
		String resourceType = matcher.group(1);
		String publicId = matcher.group(2);
		int extension = publicId.lastIndexOf('.');
		if (!"raw".equals(resourceType) && extension > publicId.lastIndexOf('/')) {
			publicId = publicId.substring(0, extension);
		}
		return new CloudinaryPath(resourceType, publicId);
	}

	private record CloudinaryPath(String resourceType, String publicId) {}
}
