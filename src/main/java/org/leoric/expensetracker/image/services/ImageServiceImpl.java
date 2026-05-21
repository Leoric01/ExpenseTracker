package org.leoric.expensetracker.image.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.image.services.interfaces.ImageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Objects;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class ImageServiceImpl implements ImageService {

	private static final Pattern CLOUDINARY_PUBLIC_ID_PATTERN = Pattern.compile(".*/upload/(?:v\\d+/)?(.+)");

	private final Cloudinary cloudinary;

	@Override
	public String uploadImage(MultipartFile file, String folder) {
		try {
			Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
					"folder", folder,
					"resource_type", "image"
			));
			String secureUrl = (String) result.get("secure_url");
			log.info("Image uploaded to Cloudinary: {}", secureUrl);
			return secureUrl;
		} catch (IOException e) {
			throw new RuntimeException("Failed to upload image to Cloudinary", e);
		}
	}

	@Override
	public String uploadFile(MultipartFile file, String folder) {
		try {
			Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
					"folder", folder,
					"resource_type", "auto"
			));
			String secureUrl = (String) result.get("secure_url");
			log.info("File uploaded to Cloudinary: {}", secureUrl);
			return secureUrl;
		} catch (IOException e) {
			throw new RuntimeException("Failed to upload file to Cloudinary", e);
		}
	}

	@Override
	public boolean deleteImage(String publicId) {
		if (publicId == null || publicId.isBlank()) {
			return false;
		}

		try {
			Map<?, ?> result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
			String deletionResult = Objects.toString(result.get("result"), "");
			boolean success = "ok".equalsIgnoreCase(deletionResult) || "not found".equalsIgnoreCase(deletionResult);
			if (success) {
				log.info("Image deleted from Cloudinary: {} ({})", publicId, deletionResult);
			} else {
				log.warn("Cloudinary did not confirm image deletion for {} ({})", publicId, deletionResult);
			}
			return success;
		} catch (Exception e) {
			log.warn("Failed to delete image from Cloudinary: {}", publicId, e);
			return false;
		}
	}

	@Override
	public boolean deleteImageByUrl(String imageUrl) {
		String publicId = extractPublicIdFromUrl(imageUrl);
		if (publicId == null) {
			log.warn("Could not extract Cloudinary publicId from URL: {}", imageUrl);
			return false;
		}
		return deleteImage(publicId);
	}

	private String extractPublicIdFromUrl(String imageUrl) {
		if (imageUrl == null || imageUrl.isBlank()) {
			return null;
		}

		String withoutQuery = imageUrl.split("\\?", 2)[0];
		Matcher matcher = CLOUDINARY_PUBLIC_ID_PATTERN.matcher(withoutQuery);
		if (!matcher.matches()) {
			return null;
		}

		String publicIdWithExtension = matcher.group(1);
		int dotIndex = publicIdWithExtension.lastIndexOf('.');
		if (dotIndex <= 0) {
			return publicIdWithExtension;
		}
		return publicIdWithExtension.substring(0, dotIndex);
	}
}