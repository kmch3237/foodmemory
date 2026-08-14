package com.foodmemory.app.entity;

import jakarta.persistence.Column;
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
 * 공유 공간 — space 테이블과 매핑된다.
 *
 * 연인, 친구, 동호회처럼 여러 사람이 함께 사진을 올리고 서로의 기록을 보는 곳이다.
 * '제주도 여행', '우리 동호회' 처럼 목적 단위로 만든다.
 *
 * 게시물은 space_id 를 가질 수도 있고 안 가질 수도 있다.
 *   NULL     → 개인 기록. 작성자만 본다
 *   값 있음  → 그 공간의 참여자 전원이 본다
 */
@Entity
@Table(name = "space")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Space extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long spaceId;

    @Column(nullable = false, length = 50)
    private String name;

    /**
     * 만든 사람.
     *
     * 참여자와 별도로 두는 이유:
     *   나중에 공간 이름 변경, 초대 코드 재발급, 공간 삭제처럼
     *   아무나 하면 안 되는 동작이 생긴다. 그때 기준이 되는 값이다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private Member owner;

    /**
     * 초대 코드. 이 값을 아는 사람이 공간에 참여할 수 있다.
     *
     * 코드 자체가 열쇠이므로 짧거나 규칙적이면 안 된다.
     * 1, 2, 3 처럼 순번이면 남의 공간을 순서대로 열어볼 수 있다.
     * 그래서 예측할 수 없는 난수로 만든다. 만드는 방법은 SpaceService 에 있다.
     *
     * UNIQUE 인 이유: 코드로 공간을 찾으므로 겹치면 어느 공간인지 정할 수 없다.
     */
    @Column(nullable = false, length = 16, unique = true)
    private String inviteCode;

    public static Space create(String name, Member owner, String inviteCode) {
        Space space = new Space();
        space.name = name;
        space.owner = owner;
        space.inviteCode = inviteCode;
        return space;
    }

    /**
     * 초대 코드를 새로 발급한다.
     *
     * 코드가 밖으로 새면 모르는 사람이 들어올 수 있다. 그때 코드를 갈아끼우면
     * 이전 코드는 더 이상 통하지 않는다. 이미 들어와 있는 사람은 그대로 남는다.
     */
    public void renewInviteCode(String newCode) {
        this.inviteCode = newCode;
    }

    public boolean isOwnedBy(Long memberId) {
        return owner.getMemberId().equals(memberId);
    }
}
