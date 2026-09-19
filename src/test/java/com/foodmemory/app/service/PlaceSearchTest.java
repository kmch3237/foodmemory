package com.foodmemory.app.service;

import com.foodmemory.app.common.ForbiddenException;
import com.foodmemory.app.common.KakaoLocalClient;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 근처 가게 찾기.
 *
 * 결과가 사진 좌표에서 가까운 순이라, 그 자체로 찍은 곳을 드러낸다.
 * 그래서 좌표를 어디서 읽는지(파일이 아니라 DB), 누가 부를 수 있는지(올린 사람만)를 본다.
 *
 * 카카오 API 는 가짜로 바꿔 끼운다. 테스트가 바깥 서비스에 요청을 보내지 않게 하고,
 * 어떤 좌표로 불렸는지 확인하기 위해서다.
 */
@SpringBootTest
@Transactional
class PlaceSearchTest {

    private static final BigDecimal LAT = new BigDecimal("37.5500000");
    private static final BigDecimal LNG = new BigDecimal("126.9200000");

    @Autowired PostService postService;
    @Autowired EntityManager em;

    @MockitoBean KakaoLocalClient kakaoLocalClient;

    Member me;
    Member mom;
    Post myPost;

    @BeforeEach
    void setUp() {
        me = persist(Member.signUp("나", null));
        mom = persist(Member.signUp("엄마", null));

        Space family = persist(Space.create("가족방", me, "T" + UUID.randomUUID().toString().substring(0, 7)));
        persist(SpaceMember.join(family, me));
        persist(SpaceMember.join(family, mom));

        myPost = persist(Post.create(me, family, null, "점심", LocalDateTime.now()));
        // 파일 경로는 없는 파일이다. 좌표를 파일에서 읽으려 하면 아무것도 못 찾는다.
        Photo photo = Photo.create(myPost, "test/none.jpg", null);
        photo.recordLocation(LAT, LNG);
        persist(photo);

        em.flush();
        em.clear();

        given(kakaoLocalClient.isConfigured()).willReturn(true);
        given(kakaoLocalClient.searchNearby(any(), any())).willReturn(List.of());
    }

    @Test
    @DisplayName("좌표는 파일이 아니라 DB 에 남긴 값으로 찾는다")
    void usesStoredLocation() {
        postService.findPlaceCandidates(myPost.getPostId(), null, me.getMemberId());

        verify(kakaoLocalClient).searchNearby(LAT, LNG);
    }

    @Test
    @DisplayName("같은 방 사람이라도 남의 사진으로는 근처 가게를 찾을 수 없다")
    void othersCannotSearch() {
        assertThatThrownBy(() -> postService.findPlaceCandidates(myPost.getPostId(), null, mom.getMemberId()))
                .isInstanceOf(ForbiddenException.class);

        verify(kakaoLocalClient, never()).searchNearby(any(), any());
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }
}
