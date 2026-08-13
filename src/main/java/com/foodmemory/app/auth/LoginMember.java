package com.foodmemory.app.auth;

import com.foodmemory.app.entity.Member;

import java.io.Serializable;

/**
 * 세션에 담아두는 '지금 로그인한 사람'.
 *
 * Member 엔티티를 그대로 담지 않는 이유가 이 클래스의 존재 이유다.
 *
 *   1. 엔티티는 JPA 가 관리하는 객체다. 세션에 넣는 순간 영속성 컨텍스트 밖으로 나가
 *      LAZY 로딩이 필요한 필드를 건드리면 예외가 난다.
 *   2. 세션에 넣은 값은 그 시점에 멈춘 사본이다. 회원이 닉네임을 바꿔도 세션 속 엔티티는
 *      옛 이름 그대로다. 엔티티처럼 생긴 것이 실제 DB 와 다르면 반드시 헷갈린다.
 *   3. 세션에는 비밀번호 해시까지 통째로 들어간다. 화면에 필요한 것은 이름 정도다.
 *
 * 그래서 화면에 필요한 최소한만 복사해서 담는다.
 *
 * Serializable 을 붙인 이유:
 *   세션은 메모리에만 있지 않다. 서버를 여러 대로 늘리거나 재시작 후에도 로그인을
 *   유지하려면 세션을 직렬화해 저장해야 한다. 그때 이 표시가 없으면 저장에 실패한다.
 */
public record LoginMember(
        Long memberId,
        String nickname
) implements Serializable {

    public static LoginMember from(Member member) {
        return new LoginMember(member.getMemberId(), member.getNickname());
    }
}
