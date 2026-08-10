package com.rivermh.soratrip.global.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
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
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp");

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

            // 저장 파일명은 UUID + 화이트리스트 확장자로만 구성되므로 이론상 항상 targetDir 하위여야 하지만,
            // delete()와 동일하게 방어적으로 한 번 더 검증한다.
            if (!targetPath.startsWith(targetDir)) {
                throw new IllegalArgumentException("허용되지 않는 파일 이름입니다.");
            }

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

    // 원본 파일명에서 확장자를 뽑아 화이트리스트와 대조한다.
    // 원본 파일명을 신뢰해 그대로 저장 경로에 쓰면 "a./../../evil.jsp" 같은 이름으로
    // 업로드 디렉토리 밖에 임의 파일을 쓸 수 있으므로, 알려진 이미지 확장자가 아니면 거부한다.
    private String extractExtension(String originalFileName) {
        if (originalFileName == null) {
            throw new IllegalArgumentException("허용되지 않는 파일 확장자입니다.");
        }
        int dotIndex = originalFileName.lastIndexOf('.');
        String extension = dotIndex >= 0 ? originalFileName.substring(dotIndex).toLowerCase() : "";
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("허용되지 않는 파일 확장자입니다.");
        }
        return extension;
    }
}
