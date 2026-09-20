package com.foodmemory.app.service;

import com.foodmemory.app.common.ForbiddenException;
import com.foodmemory.app.common.NotFoundException;
import com.foodmemory.app.entity.Member;
import com.foodmemory.app.entity.Photo;
import com.foodmemory.app.entity.Post;
import com.foodmemory.app.entity.Space;
import com.foodmemory.app.entity.SpaceMember;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
 */
@SpringBootTest
@Transactional
class PhotoAccessTest {

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
    void setUp() {
        me = persist(Member.signUp("나", null));
        mom = persist(Member.signUp("엄마", null));
        stranger = persist(Member.signUp("남", null));

        family = persist(Space.create("가족방", me, "T" + UUID.randomUUID().toString().substring(0, 7)));
        persist(SpaceMember.join(family, me));
        persist(SpaceMember.join(family, mom));

        Post inSpace = persist(Post.create(me, family, null, "점심", LocalDateTime.now()));
        spacePhoto = persist(Photo.create(inSpace, "2026/09/lunch.jpg", "2026/09/lunch_thumb.jpg"));

        Post alone = persist(Post.create(me, null, null, "혼밥", LocalDateTime.now()));
        personalPhoto = persist(Photo.create(alone, "2026/09/alone.jpg", "2026/09/alone_thumb.jpg"));

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("올린 사람은 자기 사진을 볼 수 있다")
    void ownerCanView() {
        String path = postService.getViewablePhotoPath(spacePhoto.getPhotoId(), me.getMemberId(), false);

        assertThat(path).isEqualTo("2026/09/lunch.jpg");
    }

    @Test
    @DisplayName("같은 방 사람은 남이 올린 사진도 볼 수 있다")
    void spaceMemberCanView() {
        String path = postService.getViewablePhotoPath(spacePhoto.getPhotoId(), mom.getMemberId(), false);

        assertThat(path).isEqualTo("2026/09/lunch.jpg");
    }

    @Test
    @DisplayName("방에 없는 사람은 사진 번호를 알아도 볼 수 없다")
    void strangerCannotView() {
        assertThatThrownBy(() ->
                postService.getViewablePhotoPath(spacePhoto.getPhotoId(), stranger.getMemberId(), false))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("로그인하지 않았으면 볼 수 없다")
    void anonymousCannotView() {
        assertThatThrownBy(() ->
                postService.getViewablePhotoPath(spacePhoto.getPhotoId(), null, false))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("혼자 보는 기록의 사진은 올린 사람만 볼 수 있다")
    void personalPhotoIsOwnerOnly() {
        assertThat(postService.getViewablePhotoPath(personalPhoto.getPhotoId(), me.getMemberId(), false))
                .isEqualTo("2026/09/alone.jpg");

        assertThatThrownBy(() ->
                postService.getViewablePhotoPath(personalPhoto.getPhotoId(), mom.getMemberId(), false))
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
    void leavingSpaceEndsAccess() {
        assertThat(postService.getViewablePhotoPath(spacePhoto.getPhotoId(), mom.getMemberId(), false))
                .isEqualTo("2026/09/lunch.jpg");

        spaceService.leaveAll(mom.getMemberId());
        em.flush();
        em.clear();

        assertThatThrownBy(() ->
                postService.getViewablePhotoPath(spacePhoto.getPhotoId(), mom.getMemberId(), false))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("작은 사본을 달라고 하면 사본 경로가 나온다")
    void thumbnailPath() {
        String path = postService.getViewablePhotoPath(spacePhoto.getPhotoId(), me.getMemberId(), true);

        assertThat(path).isEqualTo("2026/09/lunch_thumb.jpg");
    }

    /**
     * HEIC 처럼 자바가 못 읽는 형식은 작은 사본을 못 만든다.
     * 그때 빈칸이 되지 않도록 원본이 대신 나간다(Photo.getDisplayPath).
     */
    @Test
    @DisplayName("작은 사본이 없는 사진은 원본이 대신 나간다")
    void fallsBackToOriginalWhenNoThumbnail() {
        Post post = persist(Post.create(me, family, null, "HEIC", LocalDateTime.now()));
        Photo noThumb = persist(Photo.create(post, "2026/09/heic.jpg", null));
        em.flush();
        em.clear();

        String path = postService.getViewablePhotoPath(noThumb.getPhotoId(), me.getMemberId(), true);

        assertThat(path).isEqualTo("2026/09/heic.jpg");
    }

    @Test
    @DisplayName("없는 사진 번호는 404 로 끊는다")
    void unknownPhoto() {
        assertThatThrownBy(() ->
                postService.getViewablePhotoPath(999_999L, me.getMemberId(), false))
                .isInstanceOf(NotFoundException.class);
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }
}
