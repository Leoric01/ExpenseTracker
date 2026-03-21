package org.leoric.expensetracker.image.services.interfaces;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public interface ImageService {

	String uploadImage(MultipartFile file, String folder);

	void deleteImage(String publicId);
}