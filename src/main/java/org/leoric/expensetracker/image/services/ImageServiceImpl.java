package org.leoric.expensetracker.image.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.image.services.interfaces.ImageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class ImageServiceImpl implements ImageService {

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
	public void deleteImage(String publicId) {
		try {
			cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
			log.info("Image deleted from Cloudinary: {}", publicId);
		} catch (Exception e) {
			log.warn("Failed to delete image from Cloudinary: {}", publicId, e);
		}
	}
}