package com.foodmemory.app.entity;

import jakarta.persistence.Entity;
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
 * 공간 참여 — space_member 테이블과 매핑된다.
 *
 * "누가 어느 공간에 속해 있는가" 만 담는다. 회원과 공간의 다대다 관계를 풀어놓은 표다.
 * 한 사람이 여러 공간에 속할 수 있고, 한 공간에 여러 사람이 속한다.
 *
 * 이 표가 곧 접근 권한이다. 여기 없는 사람은 그 공간의 게시물을 볼 수 없다.
 */
@Entity
@Table(name = "space_member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpaceMember extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long spaceMemberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "space_id", nullable = false)
    private Space space;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    public static SpaceMember join(Space space, Member member) {
        SpaceMember spaceMember = new SpaceMember();
        spaceMember.space = space;
        spaceMember.member = member;
        return spaceMember;
    }
}
