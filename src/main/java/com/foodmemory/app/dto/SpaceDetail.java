package com.foodmemory.app.dto;

import java.util.List;

/**
 * 공간 화면에 보여줄 정보.
 *
 * @param inviteCode 초대 코드. 이 값이 곧 열쇠라 참여자에게만 내려간다
 * @param memberNicknames 참여자 이름들. 누구와 함께 있는지 보여준다
 */
public record SpaceDetail(
        Long spaceId,
        String name,
        String inviteCode,
        List<String> memberNicknames,
        boolean owner
) {
}
