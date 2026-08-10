package com.foodmemory.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원 — member 테이블과 매핑된다.
 */
@Entity                       // 이 클래스를 JPA가 관리하는 엔티티로 등록한다
@Table(name = "member")       // 매핑할 테이블 이름. 생략하면 클래스 이름을 쓴다
@Getter                       // Lombok이 getter를 만들어준다. setter는 일부러 만들지 않는다
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

    /**
     * PK.
     * GenerationType.IDENTITY = DB의 AUTO_INCREMENT에 맡긴다는 뜻이다.
     * Oracle이었다면 SEQUENCE 전략을 썼을 자리다.
     *
     * 타입이 long이 아니라 Long인 이유:
     *   아직 저장되지 않은 객체는 id가 "없는" 상태여야 한다.
     *   long은 0이 되어버려서 "0번 회원"과 구분이 안 된다. Long이면 null이 된다.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long memberId;

    /**
     * 컬럼 이름을 안 적었는데도 provider_user_id 를 찾아간다.
     * Spring Boot의 기본 네이밍 전략이 camelCase를 snake_case로 바꿔주기 때문이다.
     *   providerUserId  →  provider_user_id
     * MyBatis에서 map-underscore-to-camel-case: true 로 켜줬던 그 동작이
     * JPA에서는 기본값이다.
     *
     * nullable = false  →  DDL의 NOT NULL 과 대응된다.
     * length = 20       →  DDL의 VARCHAR(20) 과 대응된다.
     * ddl-auto: validate 가 이 값들이 실제 테이블과 같은지 검사한다.
     */
    @Column(nullable = false, length = 20)
    private String provider;

    @Column(nullable = false, length = 100)
    private String providerUserId;

    @Column(nullable = false, length = 50)
    private String nickname;

    // nullable을 안 적으면 기본값이 true다. 즉 NULL 허용.
    @Column(length = 255)
    private String email;

    /**
     * 소셜 로그인으로 가입한 회원을 생성한다.
     *
     * setter 를 열어두는 대신 이런 정적 팩토리 메서드를 쓰는 이유:
     *   - new Member() 보다 '가입한다'는 의도가 코드에 남는다
     *   - 가입에 반드시 필요한 값을 빠뜨릴 수 없게 강제할 수 있다
     *   - 값을 바꿀 수 있는 통로를 이 메서드 하나로 좁혀둔다
     *
     * createdAt / updatedAt 은 여기서 넣지 않는다. BaseEntity 의 Auditing 이 채운다.
     */
    public static Member signUp(String provider, String providerUserId,
                                String nickname, String email) {
        Member member = new Member();
        member.provider = provider;
        member.providerUserId = providerUserId;
        member.nickname = nickname;
        member.email = email;
        return member;
    }
}
