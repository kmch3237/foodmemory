package com.foodmemory.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 로그인 수단 — member_identity 테이블과 매핑된다.
 *
 * 한 회원이 여러 개를 가질 수 있다.
 *
 *   강명철
 *     ├─ 카카오로 들어오는 문
 *     ├─ 구글로 들어오는 문
 *     └─ 이메일+비밀번호로 들어오는 문
 *
 * 어느 문으로 들어와도 도착하는 회원은 같다. 그래서 사진첩이 하나로 유지된다.
 */
@Entity
@Table(name = "member_identity")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberIdentity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long identityId;

    /**
     * 이 수단의 주인.
     *
     * FetchType.LAZY 인 이유:
     *   기본값(EAGER)이면 로그인 수단을 조회할 때마다 회원까지 무조건 함께 가져온다.
     *   회원 정보가 필요 없는 조회에서도 쿼리가 한 번 더 나간다.
     *   필요한 곳에서 join fetch 로 명시해 가져오는 편이 낫다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Provider provider;

    /** 제공자가 매긴 ID. LOCAL 이면 이메일이 들어간다. */
    @Column(nullable = false, length = 100)
    private String providerUserId;

    /** BCrypt 해시. 소셜 로그인 수단은 null 이다. */
    @Column(length = 60)
    private String passwordHash;

    /** 소셜 로그인 수단을 만든다. */
    public static MemberIdentity ofSocial(Member member, Provider provider, String providerUserId) {
        if (provider == Provider.LOCAL) {
            throw new IllegalArgumentException("자체 로그인 수단은 ofLocal 을 사용해야 합니다.");
        }
        MemberIdentity identity = new MemberIdentity();
        identity.member = member;
        identity.provider = provider;
        identity.providerUserId = providerUserId;
        return identity;
    }

    /**
     * 이메일+비밀번호 로그인 수단을 만든다.
     *
     * 비밀번호는 이미 해싱된 값을 받는다. 원문을 받으면 이 메서드를 부르는 곳마다
     * 해싱을 잊지 않았는지 확인해야 하고, 한 곳만 빠뜨려도 원문이 그대로 저장된다.
     */
    public static MemberIdentity ofLocal(Member member, String email, String passwordHash) {
        MemberIdentity identity = new MemberIdentity();
        identity.member = member;
        identity.provider = Provider.LOCAL;
        identity.providerUserId = email;   // 자체 가입은 이메일이 곧 로그인 아이디다
        identity.passwordHash = passwordHash;
        return identity;
    }
}
