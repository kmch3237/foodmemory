package com.foodmemory.app.service;

import com.foodmemory.app.dto.SpaceDetail;
import com.foodmemory.app.dto.SpaceSummary;

import java.util.List;

public interface SpaceService {

    /** 공유 공간을 만든다. 만든 사람은 자동으로 첫 참여자가 된다. */
    Long create(String name, Long ownerId);

    /** 초대 코드로 공간에 참여한다. 이미 참여 중이면 그 공간으로 안내한다. */
    Long joinByCode(String inviteCode, Long memberId);

    /** 내가 참여 중인 공간 목록. */
    List<SpaceSummary> findMySpaces(Long memberId);

    /**
     * 공간 정보를 가져온다. 참여자가 아니면 거부한다.
     *
     * 초대 코드가 담겨 나가므로 이 확인이 특히 중요하다.
     * 코드가 새면 아무나 들어올 수 있다.
     */
    SpaceDetail getDetail(Long spaceId, Long memberId);

    /** 초대 코드를 새로 발급한다. 만든 사람만 할 수 있다. */
    void renewInviteCode(Long spaceId, Long memberId);

    /** 이 사람이 그 공간의 참여자인지. 게시물 접근 권한 판단에 쓴다. */
    boolean isMember(Long spaceId, Long memberId);
}
