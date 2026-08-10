package com.foodmemory.app.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 서버의 로컬 폴더에 파일을 저장하는 구현체.
 * 개발 단계에서 사용하고, 배포 시점에 S3 구현으로 교체한다.
 */
@Component
public class LocalFileStorage implements FileStorage {

    /** 허용할 확장자. 목록에 없는 것은 저장하지 않는다. */
    private static final List<String> ALLOWED = List.of("jpg", "jpeg", "png", "gif", "webp", "heic");

    private final Path root;

    /**
     * @Value 는 application.yml 의 값을 가져온다.
     * app.upload.path 에 적어둔 값이 여기로 들어온다.
     */
    public LocalFileStorage(@Value("${app.upload.path}") String uploadPath) {
        this.root = Paths.get(uploadPath).toAbsolutePath().normalize();
    }

    @Override
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("빈 파일입니다.");
        }

        String extension = extractExtension(file.getOriginalFilename());

        // 사용자가 준 파일명은 쓰지 않고 서버가 새로 만든다.
        //   - 같은 이름이 올라오면 앞 사람 파일을 덮어쓴다
        //   - 한글·공백·특수문자로 URL 문제가 생긴다
        //   - ../../ 같은 경로 조작이나 실행 가능한 확장자 업로드를 막는다
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;

        // 한 폴더에 파일이 수만 개 쌓이면 느려지므로 연/월로 나눈다.
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String relativePath = datePath + "/" + fileName;

        try {
            Path target = root.resolve(relativePath);
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            throw new IllegalStateException("파일 저장에 실패했습니다.", e);
        }

        // DB 에 저장될 값. 폴더 위치도 도메인도 포함하지 않는다.
        return relativePath;
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new IllegalArgumentException("확장자가 없는 파일입니다.");
        }
        String ext = originalFilename
                .substring(originalFilename.lastIndexOf(".") + 1)
                .toLowerCase(Locale.ROOT);

        if (!ALLOWED.contains(ext)) {
            throw new IllegalArgumentException("허용하지 않는 형식입니다: " + ext);
        }
        return ext;
    }
}
