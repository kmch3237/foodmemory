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

import java.time.LocalDateTime;

/**
 * 회원 — member 테이블과 매핑된다.
 *
 * 여기에는 '로그인하는 방법' 이 없다. 그건 MemberIdentity 가 맡는다.
 * 이 클래스는 '사람' 이고, MemberIdentity 는 '그 사람이 들어오는 문' 이다.
 *
 * 나눈 이유:
 *   한 사람이 카카오로도 구글로도 들어올 수 있다.
 *   로그인 정보를 이 클래스에 두면 들어오는 문마다 다른 회원이 되어 사진첩이 쪼개진다.
 *
 * 게시물은 계속 이 회원을 바라본다. 로그인 수단이 늘어도 게시물의 주인은 흔들리지 않는다.
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
     * nullable = false  →  DDL의 NOT NULL 과 대응된다.
     * length = 50       →  DDL의 VARCHAR(50) 과 대응된다.
     * ddl-auto: validate 가 이 값들이 실제 테이블과 같은지 검사한다.
     */
    @Column(nullable = false, length = 50)
    private String nickname;

    // nullable을 안 적으면 기본값이 true다. 즉 NULL 허용.
    @Column(length = 255)
    private String email;

    /**
     * 약관에 동의한 시각. 동의하지 않고는 가입할 수 없으므로 새 회원은 항상 값이 있다.
     *
     * 사람 단위의 동의라 여기에 둔다.
     * 로그인 수단을 하나 더 연결한다고 약관에 다시 동의해야 하는 것은 아니다.
     */
    private LocalDateTime termsAgreedAt;

    /**
     * 회원을 만든다.
     *
     * 로그인 수단은 받지 않는다. 회원과 로그인 수단은 따로 저장되고,
     * 그 둘을 짝지어 만드는 일은 Service 가 한 트랜잭션 안에서 처리한다.
     *
     * setter 를 열어두는 대신 정적 팩토리 메서드를 쓰는 이유:
     *   - new Member() 보다 '가입한다'는 의도가 코드에 남는다
     *   - 가입에 반드시 필요한 값을 빠뜨릴 수 없게 강제할 수 있다
     *   - 값을 바꿀 수 있는 통로를 이 메서드 하나로 좁혀둔다
     *
     * createdAt / updatedAt 은 여기서 넣지 않는다. BaseEntity 의 Auditing 이 채운다.
     */
    public static Member signUp(String nickname, String email) {
        Member member = new Member();
        member.nickname = nickname;
        member.email = email;
        member.termsAgreedAt = LocalDateTime.now();
        return member;
    }

    /**
     * 대표 이메일을 채운다.
     *
     * 이미 값이 있으면 덮어쓰지 않는다.
     * 카카오로 먼저 가입해 이메일이 없던 사람이 나중에 구글을 연결하면 그때 채워지는데,
     * 반대로 이미 이메일이 있는 사람이 새 수단을 붙였다고 대표 이메일이 바뀌면
     * 본인도 모르는 사이에 연락처가 갈아치워진다.
     */
    public void fillEmailIfMissing(String candidate) {
        if ((this.email == null || this.email.isBlank())
                && candidate != null && !candidate.isBlank()) {
            this.email = candidate;
        }
    }
}
