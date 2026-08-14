package com.foodmemory.app.dto;

/**
 * 공간 목록에 보여줄 요약.
 *
 * inviteCode 를 담지 않는다. 목록 화면에 코드를 뿌리면 화면 소스에 코드가 남고,
 * 어깨너머로 보이기도 한다. 코드는 초대하려고 들어간 화면에서만 보여준다.
 */
public record SpaceSummary(
        Long spaceId,
        String name,
        long memberCount,
        boolean owner        // 내가 만든 공간인지. 초대 코드 재발급 버튼을 보여줄지 판단한다
) {
}
