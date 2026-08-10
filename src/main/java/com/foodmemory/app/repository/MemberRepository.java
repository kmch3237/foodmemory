package com.foodmemory.app.repository;

import com.foodmemory.app.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 회원 저장소.
 *
 * 인터페이스만 선언하고 구현 클래스는 만들지 않는다.
 * Spring Data JPA 가 실행 시점에 구현체를 만들어 주입해준다.
 *
 * JpaRepository<Member, Long> 의 제네릭은 <엔티티 타입, PK 타입> 이다.
 * 이 선언만으로 save / findById / findAll / delete / count 등이 생긴다.
 */
public interface MemberRepository extends JpaRepository<Member, Long> {

    /**
     * 쿼리 메서드 — 메서드 이름만 보고 Spring Data JPA 가 SQL 을 만들어준다.
     *
     *   findBy  Provider  And  ProviderUserId
     *           ↓              ↓
     *   WHERE provider = ? AND provider_user_id = ?
     *
     * MyBatis 였다면 XML 에 직접 SELECT 문을 썼을 자리다.
     *
     * 반환 타입이 Optional 인 이유:
     *   조회 결과가 없을 수 있다는 사실을 타입으로 드러내기 위해서다.
     *   null 을 그대로 돌려주면 받는 쪽에서 검사를 빠뜨려 NullPointerException 이 난다.
     *
     * 소셜 로그인 시 "이미 가입한 회원인지" 확인하는 데 쓰게 된다.
     * provider + provider_user_id 에 UNIQUE 를 걸어두었으므로 결과는 0건 또는 1건이다.
     */
    Optional<Member> findByProviderAndProviderUserId(String provider, String providerUserId);
}
