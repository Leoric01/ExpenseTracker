package org.leoric.expensetracker.image.services;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

record StoredFileData(String contentType, byte[] bytes) implements MultipartFile {
	@Override public String getName() { return "file"; }
	@Override public String getOriginalFilename() { return "file"; }
	@Override public String getContentType() { return contentType; }
	@Override public boolean isEmpty() { return bytes.length == 0; }
	@Override public long getSize() { return bytes.length; }
	@Override public byte[] getBytes() { return bytes; }
	@Override public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
	@Override public void transferTo(java.io.File destination) throws IOException { Files.write(destination.toPath(), bytes); }
	@Override public void transferTo(Path destination) throws IOException { Files.write(destination, bytes); }
}
