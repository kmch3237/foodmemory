package com.foodmemory.app.dto;

import com.foodmemory.app.entity.Post;

import java.time.LocalDateTime;

/**
 * 수정 폼에 미리 채워 넣을 값.
 *
 * 상세용(PostDetailResponse)을 그대로 쓰지 않는 이유:
 *   상세 화면은 사진 전부와 작성자 닉네임, 장소 이름·주소까지 필요하지만
 *   수정 폼에 들어가는 입력칸은 코멘트·먹은 날짜·공간 셋뿐이다.
 *   화면이 쓰지도 않을 값을 담아 나르면, 나중에 이 DTO 를 읽는 사람이
 *   "이 값도 폼에서 바꿀 수 있나?" 하고 오해한다.
 *   DTO 는 화면 단위로 나눈다는 규칙을 여기서도 지킨다.
 *
 * 엔티티(Post)를 화면에 그대로 넘기지 않는 이유는 더 크다.
 *   엔티티를 넘기면 화면에서 post.getMember().getEmail() 같은 것까지 꺼낼 수 있고,
 *   실수로 그런 코드가 들어가면 화면에 남의 개인정보가 찍힌다.
 *   DTO 는 "화면에 내보내도 되는 값" 만 골라 담은 통행증이다.
 */
public record PostEditForm(
        Long postId,
        String content,
        LocalDateTime eatenDate,

        /**
         * 지금 이 기록이 올라가 있는 공간. 개인 기록이면 null 이다.
         *
         * 이름이 아니라 번호를 담는 이유는 화면에서 셀렉트 박스의 어느 항목을
         * 선택된 상태로 표시할지 비교하는 데 쓰기 때문이다. 이름은 중복될 수 있다.
         */
        Long spaceId
) {

    public static PostEditForm from(Post post) {
        return new PostEditForm(
                post.getPostId(),
                post.getContent(),
                post.getEatenDate(),
                post.getSpace() != null ? post.getSpace().getSpaceId() : null
        );
    }
}
