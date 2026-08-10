package com.foodmemory.app.repository;

import com.foodmemory.app.entity.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 회원 저장과 조회가 실제로 동작하는지 확인한다.
 *
 * @SpringBootTest 는 애플리케이션을 실제로 띄워서 테스트한다.
 * 따라서 실제 MySQL 에 연결되고, 콘솔에 JPA 가 만든 SQL 이 그대로 찍힌다.
 */
@SpringBootTest
@Transactional   // 각 테스트가 끝나면 자동으로 롤백된다. 테스트를 몇 번 돌려도 결과가 같아진다
class MemberRepositoryTest {

    @Autowired
    MemberRepository memberRepository;

    @Test
    @DisplayName("회원을 저장하면 감사 컬럼이 자동으로 채워진다")
    void save() {
        // given — 저장할 회원을 만든다. createdAt / updatedAt 은 넣지 않는다.
        Member member = Member.signUp("kakao", "12345", "철수", null);

        System.out.println("=== 저장 전 ===");
        System.out.println("memberId  = " + member.getMemberId());
        System.out.println("createdAt = " + member.getCreatedAt());

        // when — 저장한다. 여기서 INSERT 문이 나간다.
        Member saved = memberRepository.save(member);

        System.out.println("=== 저장 후 ===");
        System.out.println("memberId  = " + saved.getMemberId());
        System.out.println("createdAt = " + saved.getCreatedAt());
        System.out.println("updatedAt = " + saved.getUpdatedAt());

        // then
        assertThat(saved.getMemberId()).isNotNull();     // AUTO_INCREMENT 로 번호가 매겨졌다
        assertThat(saved.getCreatedAt()).isNotNull();    // Auditing 이 채웠다
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getEmail()).isNull();           // 이메일 없이도 저장된다
    }

    @Test
    @DisplayName("소셜 계정으로 기존 회원을 찾을 수 있다")
    void findByProviderAndProviderUserId() {
        // given
        memberRepository.save(Member.signUp("google", "99999", "영희", "yh@example.com"));

        // when — 메서드 이름만으로 만들어진 SELECT 문이 나간다
        Optional<Member> found =
                memberRepository.findByProviderAndProviderUserId("google", "99999");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getNickname()).isEqualTo("영희");

        // 가입한 적 없는 계정은 비어 있다
        Optional<Member> notFound =
                memberRepository.findByProviderAndProviderUserId("naver", "00000");
        assertThat(notFound).isEmpty();
    }
}
