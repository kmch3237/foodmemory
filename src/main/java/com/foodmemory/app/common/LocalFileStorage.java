package com.foodmemory.app.common;

import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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

    @Override
    public Path resolve(String relativePath) {
        return root.resolve(relativePath);
    }

    @Override
    public boolean delete(String relativePath) {
        try {
            Path target = root.resolve(relativePath).normalize();

            /*
             * 지우려는 경로가 정말 업로드 폴더 안인지 확인한다.
             *
             * DB 에 들어 있는 값을 그대로 믿지 않는 이유:
             *   경로에 ../../ 가 섞여 있으면 root.resolve 는 폴더 밖을 가리키게 된다.
             *   그 상태로 삭제하면 서버의 엉뚱한 파일이 지워진다.
             *   지금은 저장할 때 서버가 경로를 만들지만, 나중에 다른 경로가 들어올
             *   길이 생겼을 때 이 한 줄이 남아 있으면 사고가 나지 않는다.
             */
            if (!target.startsWith(root)) {
                log.warn("업로드 폴더 밖의 경로는 삭제하지 않습니다: {}", relativePath);
                return false;
            }

            // deleteIfExists 는 파일이 없어도 예외를 던지지 않는다.
            // 이미 지워진 파일을 다시 지우려는 상황은 오류가 아니다.
            return Files.deleteIfExists(target);

        } catch (IOException e) {
            // 던지지 않고 기록만 남긴다. 이유는 FileStorage 인터페이스에 적어두었다.
            log.warn("파일 삭제에 실패했습니다: {}", relativePath, e);
            return false;
        }
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
