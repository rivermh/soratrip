package com.rivermh.soratrip.global.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
    private static final DateTimeFormatter DATE_PATH_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    @Value("${file.upload-dir}")
    private String uploadDir;

    // 업로드 파일을 로컬 디스크에 저장하고, 브라우저에서 접근 가능한 상대 URL을 반환
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 비어 있습니다.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
        }

        try {
            String datePath = LocalDate.now().format(DATE_PATH_FORMAT);
            Path targetDir = Paths.get(uploadDir, datePath).toAbsolutePath().normalize();
            Files.createDirectories(targetDir);

            String extension = extractExtension(file.getOriginalFilename());
            String storedFileName = UUID.randomUUID() + extension;
            Path targetPath = targetDir.resolve(storedFileName).normalize();

            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            return "/uploads/" + datePath + "/" + storedFileName;
        } catch (IOException e) {
            throw new IllegalStateException("파일 저장 중 오류가 발생했습니다.", e);
        }
    }

    // 저장된 파일 삭제. 실패해도 예외를 던지지 않고 로그만 남긴다 (트랜잭션을 막지 않기 위함)
    public void delete(String relativeUrl) {
        if (relativeUrl == null || !relativeUrl.startsWith("/uploads/")) {
            return;
        }
        try {
            Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path targetPath = uploadRoot.resolve(relativeUrl.substring("/uploads/".length())).normalize();

            if (!targetPath.startsWith(uploadRoot)) {
                log.warn("업로드 경로를 벗어난 삭제 요청을 무시했습니다: {}", relativeUrl);
                return;
            }
            Files.deleteIfExists(targetPath);
        } catch (IOException e) {
            log.warn("파일 삭제에 실패했습니다: {}", relativeUrl, e);
        }
    }

    private String extractExtension(String originalFileName) {
        if (originalFileName == null) {
            return "";
        }
        int dotIndex = originalFileName.lastIndexOf('.');
        return dotIndex >= 0 ? originalFileName.substring(dotIndex) : "";
    }
}
