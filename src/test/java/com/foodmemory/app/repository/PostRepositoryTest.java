package com.foodmemory.app.repository;

import com.foodmemory.app.entity.Member;
import com.foodmemory.app.entity.Post;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PostRepositoryTest {

    @Autowired PostRepository postRepository;
    @Autowired MemberRepository memberRepository;

    /**
     * EntityManager 는 JPA 의 핵심 객체다.
     * 여기서는 영속성 컨텍스트(1차 캐시)를 비우는 용도로만 쓴다.
     *
     * 비우지 않으면 방금 저장한 객체가 메모리에 남아 있어서
     * 조회해도 DB 에 쿼리가 나가지 않는다. 그러면 LAZY 동작을 관찰할 수 없다.
     */
    @Autowired EntityManager em;

    @Test
    @DisplayName("게시물을 저장하면 FK가 member_id 컬럼에 들어간다")
    void save() {
        Member member = memberRepository.save(Member.signUp("kakao", "1", "철수", null));

        Post post = postRepository.save(
                Post.create(member, null, "집에서 파스타", LocalDateTime.of(2023, 5, 12, 19, 30)));

        assertThat(post.getPostId()).isNotNull();
        assertThat(post.getMember().getNickname()).isEqualTo("철수");
        assertThat(post.getRestaurant()).isNull();   // 식당 없이도 저장된다
        assertThat(post.isPublic()).isFalse();       // 기본값은 비공개
        assertThat(post.getCreatedAt()).isNotNull(); // Auditing 이 채웠다
    }

    @Test
    @DisplayName("LAZY — 작성자에 실제로 접근할 때 비로소 조회 쿼리가 나간다")
    void lazyLoading() {
        Member member = memberRepository.save(Member.signUp("kakao", "2", "영희", null));
        Post post = postRepository.save(
                Post.create(member, null, "치킨", LocalDateTime.now()));

        em.flush();   // 쌓아둔 INSERT 를 DB 로 내보낸다
        em.clear();   // 영속성 컨텍스트를 비운다. 이제 조회하면 진짜 DB 에 간다

        System.out.println("\n>>> [1] 게시물만 조회한다");
        Post found = postRepository.findById(post.getPostId()).orElseThrow();

        System.out.println("\n>>> [2] 여기까지 회원 조회 쿼리는 나가지 않았다");

        System.out.println("\n>>> [3] 이제 작성자 닉네임에 접근한다");
        String nickname = found.getMember().getNickname();

        System.out.println("\n>>> [4] 닉네임 = " + nickname);
        assertThat(nickname).isEqualTo("영희");
    }

    @Test
    @DisplayName("N+1 — 목록 3건을 조회했는데 쿼리는 4번 나간다")
    void nPlusOne() {
        for (int i = 1; i <= 3; i++) {
            Member m = memberRepository.save(Member.signUp("kakao", "n" + i, "회원" + i, null));
            postRepository.save(Post.create(m, null, "글" + i, LocalDateTime.now()));
        }
        em.flush();
        em.clear();

        System.out.println("\n>>> [1] findAll() — 게시물 목록 조회 (쿼리 1번)");
        List<Post> posts = postRepository.findAll();

        System.out.println("\n>>> [2] 이제 작성자 닉네임을 하나씩 꺼낸다 (여기서 추가 쿼리)");
        for (Post p : posts) {
            System.out.println("     작성자 = " + p.getMember().getNickname());
        }

        System.out.println("\n>>> [3] 총 1 + 3 = 4번의 쿼리가 나갔다");
        assertThat(posts).hasSize(3);
    }

    @Test
    @DisplayName("fetch join — 같은 결과를 쿼리 1번으로 가져온다")
    void fetchJoin() {
        for (int i = 1; i <= 3; i++) {
            Member m = memberRepository.save(Member.signUp("kakao", "f" + i, "회원" + i, null));
            postRepository.save(Post.create(m, null, "글" + i, LocalDateTime.now()));
        }
        em.flush();
        em.clear();

        System.out.println("\n>>> [1] findAllWithMember() — join fetch 로 한 번에 조회");
        List<Post> posts = postRepository.findAllWithMember();

        System.out.println("\n>>> [2] 닉네임을 꺼내도 추가 쿼리가 나가지 않는다");
        for (Post p : posts) {
            System.out.println("     작성자 = " + p.getMember().getNickname());
        }

        System.out.println("\n>>> [3] 쿼리는 1번뿐이었다");
        assertThat(posts).hasSize(3);
    }
}
