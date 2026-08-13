package com.foodmemory.app.repository;

import com.foodmemory.app.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 회원 저장소.
 *
 * 인터페이스만 선언하고 구현 클래스는 만들지 않는다.
 * Spring Data JPA 가 실행 시점에 구현체를 만들어 주입해준다.
 *
 * JpaRepository<Member, Long> 의 제네릭은 <엔티티 타입, PK 타입> 이다.
 * 이 선언만으로 save / findById / findAll / delete / count 등이 생긴다.
 *
 * 로그인 수단으로 회원을 찾는 일은 여기 없다. MemberIdentityRepository 가 맡는다.
 * 회원 테이블에는 이제 로그인 정보가 없기 때문이다.
 */
public interface MemberRepository extends JpaRepository<Member, Long> {
}
