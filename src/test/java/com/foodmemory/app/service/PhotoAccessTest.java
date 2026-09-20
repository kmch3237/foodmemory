package com.foodmemory.app.service;

import com.foodmemory.app.common.ForbiddenException;
import com.foodmemory.app.common.NotFoundException;
import com.foodmemory.app.entity.Member;
import com.foodmemory.app.entity.Photo;
import com.foodmemory.app.entity.Post;
import com.foodmemory.app.entity.Space;
import com.foodmemory.app.entity.SpaceMember;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 사진을 볼 권한.
 *
 * 전에는 /uploads/2026/08/3f8c….jpg 처럼 파일 경로가 그대로 주소였고 검사가 없었다.
 * 주소를 아는 사람은 누구나 받을 수 있었고, 방에서 나가도 그 주소는 계속 살아 있었다.
 * 지금은 /photos/{번호} 로 받아 요청마다 물어본다.
 *
 * 그래서 여기서 보는 것은 '지금 볼 수 있느냐' 가 아니라
 * **관계가 끝났을 때 접근도 끝나느냐** 다. 그게 주소를 바꾼 이유다.
 *
 * 사진 폴더는 임시 폴더로 돌려놓는다. 서비스가 경로가 아니라 실제 파일을 돌려주므로
 * 파일이 진짜로 있어야 하고, 저장소의 uploads 를 건드리지 않기 위해서다.
 */
@SpringBootTest
@Transactional
class PhotoAccessTest {

    private static final Path UPLOAD_ROOT =
            Path.of(System.getProperty("java.io.tmpdir"), "mealmates-photo-access-test");

    @DynamicPropertySource
    static void uploadPath(DynamicPropertyRegistry registry) {
        registry.add("app.upload.path", UPLOAD_ROOT::toString);
    }

    @Autowired PostService postService;
    @Autowired SpaceService spaceService;
    @Autowired EntityManager em;

    Member me;
    Member mom;
    Member stranger;

    Space family;
    Photo spacePhoto;      // 가족방에 올린 사진
    Photo personalPhoto;   // 혼자 보는 사진

    @BeforeEach
    void setUp() throws IOException {
        me = persist(Member.signUp("나", null));
        mom = persist(Member.signUp("엄마", null));
        stranger = persist(Member.signUp("남", null));

        family = persist(Space.create("가족방", me, "T" + UUID.randomUUID().toString().substring(0, 7)));
        persist(SpaceMember.join(family, me));
        persist(SpaceMember.join(family, mom));

        Post inSpace = persist(Post.create(me, family, null, "점심", LocalDateTime.now()));
        spacePhoto = persist(Photo.create(inSpace,
                writeFile("2026/09/lunch.jpg"), writeFile("2026/09/lunch_thumb.jpg")));

        Post alone = persist(Post.create(me, null, null, "혼밥", LocalDateTime.now()));
        personalPhoto = persist(Photo.create(alone,
                writeFile("2026/09/alone.jpg"), writeFile("2026/09/alone_thumb.jpg")));

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("올린 사람은 자기 사진을 볼 수 있다")
    void ownerCanView() throws Exception {
        Resource photo = postService.getViewablePhoto(spacePhoto.getPhotoId(), me.getMemberId(), false);

        assertThat(photo.getFile().toPath()).isEqualTo(UPLOAD_ROOT.resolve("2026/09/lunch.jpg"));
    }

    @Test
    @DisplayName("같은 방 사람은 남이 올린 사진도 볼 수 있다")
    void spaceMemberCanView() throws Exception {
        Resource photo = postService.getViewablePhoto(spacePhoto.getPhotoId(), mom.getMemberId(), false);

        assertThat(photo.getFile().toPath()).isEqualTo(UPLOAD_ROOT.resolve("2026/09/lunch.jpg"));
    }

    @Test
    @DisplayName("방에 없는 사람은 사진 번호를 알아도 볼 수 없다")
    void strangerCannotView() {
        assertThatThrownBy(() ->
                postService.getViewablePhoto(spacePhoto.getPhotoId(), stranger.getMemberId(), false))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("로그인하지 않았으면 볼 수 없다")
    void anonymousCannotView() {
        assertThatThrownBy(() ->
                postService.getViewablePhoto(spacePhoto.getPhotoId(), null, false))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("혼자 보는 기록의 사진은 올린 사람만 볼 수 있다")
    void personalPhotoIsOwnerOnly() throws Exception {
        assertThat(postService.getViewablePhoto(personalPhoto.getPhotoId(), me.getMemberId(), false)
                .getFile().toPath())
                .isEqualTo(UPLOAD_ROOT.resolve("2026/09/alone.jpg"));

        assertThatThrownBy(() ->
                postService.getViewablePhoto(personalPhoto.getPhotoId(), mom.getMemberId(), false))
                .isInstanceOf(ForbiddenException.class);
    }

    /**
     * 이 작업의 이유가 이 테스트다.
     *
     * 주소가 파일 경로였을 때는, 방에서 나가도 그 주소를 아는 한 계속 받을 수 있었다.
     * 되돌릴 방법도 없었다. 이제는 다음 요청부터 막힌다.
     */
    @Test
    @DisplayName("방에서 나가면 그전에 보던 사진도 그때부터 막힌다")
    void leavingSpaceEndsAccess() throws Exception {
        assertThat(postService.getViewablePhoto(spacePhoto.getPhotoId(), mom.getMemberId(), false)
                .getFile().toPath())
                .isEqualTo(UPLOAD_ROOT.resolve("2026/09/lunch.jpg"));

        spaceService.leaveAll(mom.getMemberId());
        em.flush();
        em.clear();

        assertThatThrownBy(() ->
                postService.getViewablePhoto(spacePhoto.getPhotoId(), mom.getMemberId(), false))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("작은 사본을 달라고 하면 사본이 나온다")
    void thumbnail() throws Exception {
        Resource photo = postService.getViewablePhoto(spacePhoto.getPhotoId(), me.getMemberId(), true);

        assertThat(photo.getFile().toPath()).isEqualTo(UPLOAD_ROOT.resolve("2026/09/lunch_thumb.jpg"));
    }

    /**
     * HEIC 처럼 자바가 못 읽는 형식은 작은 사본을 못 만든다.
     * 그때 빈칸이 되지 않도록 원본이 대신 나간다(Photo.getDisplayPath).
     */
    @Test
    @DisplayName("작은 사본이 없는 사진은 원본이 대신 나간다")
    void fallsBackToOriginalWhenNoThumbnail() throws Exception {
        Post post = persist(Post.create(me, family, null, "HEIC", LocalDateTime.now()));
        Photo noThumb = persist(Photo.create(post, writeFile("2026/09/heic.jpg"), null));
        em.flush();
        em.clear();

        Resource photo = postService.getViewablePhoto(noThumb.getPhotoId(), me.getMemberId(), true);

        assertThat(photo.getFile().toPath()).isEqualTo(UPLOAD_ROOT.resolve("2026/09/heic.jpg"));
    }

    /**
     * 백업에서 되살리다 DB 와 파일이 어긋나면 생긴다.
     * 여기서 끊지 않으면 0바이트 이미지가 나가 원인을 찾기 어려워진다.
     */
    @Test
    @DisplayName("DB 에 경로는 있는데 파일이 없으면 404 로 끊는다")
    void missingFile() {
        Post post = persist(Post.create(me, family, null, "사라진 파일", LocalDateTime.now()));
        Photo ghost = persist(Photo.create(post, "2026/09/gone.jpg", null));
        em.flush();
        em.clear();

        assertThatThrownBy(() ->
                postService.getViewablePhoto(ghost.getPhotoId(), me.getMemberId(), false))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("없는 사진 번호는 404 로 끊는다")
    void unknownPhoto() {
        assertThatThrownBy(() ->
                postService.getViewablePhoto(999_999L, me.getMemberId(), false))
                .isInstanceOf(NotFoundException.class);
    }

    /** 상대 경로로 빈 파일을 하나 만들고, 그 상대 경로를 그대로 돌려준다. */
    private String writeFile(String relativePath) throws IOException {
        Path file = UPLOAD_ROOT.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[]{1, 2, 3});
        return relativePath;
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }

    @AfterAll
    static void cleanUp() throws IOException {
        if (!Files.exists(UPLOAD_ROOT)) {
            return;
        }
        try (var paths = Files.walk(UPLOAD_ROOT)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException ignored) {
                    // 지우다 실패해도 테스트 결과를 바꾸지 않는다. 임시 폴더다.
                }
            });
        }
    }
}
