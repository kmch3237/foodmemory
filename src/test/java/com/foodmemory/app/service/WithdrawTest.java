package com.foodmemory.app.service;

import com.foodmemory.app.entity.Comment;
import com.foodmemory.app.entity.Member;
import com.foodmemory.app.entity.MemberIdentity;
import com.foodmemory.app.entity.Photo;
import com.foodmemory.app.entity.Post;
import com.foodmemory.app.entity.Provider;
import com.foodmemory.app.entity.Space;
import com.foodmemory.app.entity.SpaceMember;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 탈퇴.
 *
 * 탈퇴는 되돌릴 수 없고, 여러 테이블을 FK 순서대로 지워야 해서 어긋나기 쉽다.
 * 게시물에 딸린 것이 하나 늘거나 회원을 가리키는 칸이 하나 생기면 여기서 먼저 깨진다.
 *
 * 로컬 MySQL 을 그대로 쓰고, 테스트마다 롤백되므로 DB 에 흔적이 남지 않는다.
 * 롤백되면 '커밋 뒤 파일 삭제' 도 실행되지 않아 로컬 사진 파일도 안전하다.
 *
 * 등장인물
 *   나   — 탈퇴하는 사람. 이메일 가입(비밀번호 있음)
 *   엄마 — 남는 사람
 *
 *   가족방   : 내가 만들고 엄마가 들어옴  → 엄마에게 넘어가야 한다
 *   혼자방   : 내가 만들고 나만 있음      → 지워져야 한다
 *   엄마방   : 엄마가 만들고 내가 들어감  → 그대로, 나만 빠져야 한다
 */
@SpringBootTest
@Transactional
class WithdrawTest {

    private static final String PASSWORD = "password123";

    @Autowired AuthService authService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager em;

    Member me;
    Member mom;
    Space familySpace;
    Space soloSpace;
    Space momSpace;
    Post myPersonalPost;
    Post myFamilyPost;
    Post momFamilyPost;

    @BeforeEach
    void setUp() {
        me = persist(Member.signUp("나", "me@test.com"));
        persist(MemberIdentity.ofLocal(me, "me-" + UUID.randomUUID() + "@test.com",
                passwordEncoder.encode(PASSWORD)));
        mom = persist(Member.signUp("엄마", null));
        persist(MemberIdentity.ofSocial(mom, Provider.KAKAO, "mom-" + UUID.randomUUID()));

        familySpace = persist(Space.create("가족방", me, code()));
        persist(SpaceMember.join(familySpace, me));
        persist(SpaceMember.join(familySpace, mom));

        soloSpace = persist(Space.create("혼자방", me, code()));
        persist(SpaceMember.join(soloSpace, me));

        momSpace = persist(Space.create("엄마방", mom, code()));
        persist(SpaceMember.join(momSpace, mom));
        persist(SpaceMember.join(momSpace, me));

        myPersonalPost = persist(Post.create(me, null, null, "혼자 먹은 라면", LocalDateTime.now()));
        persist(Photo.create(myPersonalPost, "test/a.jpg", "test/a_thumb.jpg"));

        myFamilyPost = persist(Post.create(me, familySpace, null, "가족 외식", LocalDateTime.now()));
        persist(Photo.create(myFamilyPost, "test/b.jpg", null));
        persist(Post.create(me, soloSpace, null, "혼자방 기록", LocalDateTime.now()));

        momFamilyPost = persist(Post.create(mom, familySpace, null, "엄마 김치찌개", LocalDateTime.now()));

        persist(Comment.create(momFamilyPost, me, "맛있겠다"));      // 내가 남의 글에 단 댓글
        persist(Comment.create(myFamilyPost, mom, "또 가자"));        // 남이 내 글에 단 댓글

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("탈퇴하면 나와 내가 남긴 것은 모두 지워지고, 엄마의 기록과 방은 남는다")
    void withdraw() {
        authService.withdraw(me.getMemberId(), PASSWORD, null);
        em.flush();
        em.clear();

        // 나와 내 흔적
        assertThat(em.find(Member.class, me.getMemberId())).isNull();
        assertThat(count("select count(i) from MemberIdentity i where i.member.memberId = :id")).isZero();
        assertThat(count("select count(p) from Post p where p.member.memberId = :id")).isZero();
        assertThat(count("select count(c) from Comment c where c.member.memberId = :id")).isZero();
        assertThat(count("select count(sm) from SpaceMember sm where sm.member.memberId = :id")).isZero();
        assertThat(em.createQuery("select count(ph) from Photo ph where ph.filePath like 'test/%'", Long.class)
                .getSingleResult()).isZero();

        // 엄마가 내 글에 단 댓글은 내 글과 함께 사라진다
        assertThat(em.createQuery("select count(c) from Comment c where c.content = '또 가자'", Long.class)
                .getSingleResult()).isZero();

        // 엄마의 기록은 남는다
        assertThat(em.find(Post.class, momFamilyPost.getPostId())).isNotNull();

        // 가족방은 엄마에게 넘어간다
        Space family = em.find(Space.class, familySpace.getSpaceId());
        assertThat(family).isNotNull();
        assertThat(family.isOwnedBy(mom.getMemberId())).isTrue();

        // 혼자방은 지워진다
        assertThat(em.find(Space.class, soloSpace.getSpaceId())).isNull();

        // 엄마방은 그대로, 엄마 방장 그대로
        Space momsRoom = em.find(Space.class, momSpace.getSpaceId());
        assertThat(momsRoom).isNotNull();
        assertThat(momsRoom.isOwnedBy(mom.getMemberId())).isTrue();
    }

    @Test
    @DisplayName("비밀번호가 틀리면 거부하고 아무것도 지우지 않는다")
    void wrongPassword() {
        assertThatThrownBy(() -> authService.withdraw(me.getMemberId(), "wrong-password", null))
                .isInstanceOf(IllegalArgumentException.class);

        em.flush();
        em.clear();
        assertThat(em.find(Member.class, me.getMemberId())).isNotNull();
        assertThat(count("select count(p) from Post p where p.member.memberId = :id")).isEqualTo(3);
    }

    @Test
    @DisplayName("비밀번호가 있는 회원은 '탈퇴' 입력으로 대신할 수 없다")
    void confirmTextIsNotEnoughWhenPasswordExists() {
        assertThatThrownBy(() -> authService.withdraw(me.getMemberId(), null, "탈퇴"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("소셜로만 가입한 회원은 '탈퇴' 라고 입력해야 탈퇴된다")
    void socialMember() {
        assertThatThrownBy(() -> authService.withdraw(mom.getMemberId(), null, "탈퇴할래"))
                .isInstanceOf(IllegalArgumentException.class);

        authService.withdraw(mom.getMemberId(), null, " 탈퇴 ");
        em.flush();
        em.clear();

        assertThat(em.find(Member.class, mom.getMemberId())).isNull();
        // 엄마가 만든 엄마방은 남아 있던 나에게 넘어온다
        assertThat(em.find(Space.class, momSpace.getSpaceId()).isOwnedBy(me.getMemberId())).isTrue();
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }

    private long count(String jpql) {
        return em.createQuery(jpql, Long.class).setParameter("id", me.getMemberId()).getSingleResult();
    }

    private static String code() {
        return UUID.randomUUID().toString().substring(0, 16);
    }
}
