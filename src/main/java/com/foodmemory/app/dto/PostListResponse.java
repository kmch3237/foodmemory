package com.foodmemory.app.dto;

import com.foodmemory.app.entity.Post;

import java.time.LocalDateTime;

/**
 * 갤러리 목록 화면에 내보낼 데이터.
 *
 * 엔티티(Post)를 화면에 그대로 넘기지 않는 이유:
 *   - 화면에 필요한 값과 테이블 구조는 다르다. 화면은 member_id 숫자가 아니라 닉네임이 필요하다
 *   - open-in-view: false 로 두었기 때문에, 트랜잭션이 끝난 뒤 화면에서 LAZY 로딩을 시도하면
 *     영속성 컨텍스트가 이미 닫혀 있어 예외가 난다. 값은 트랜잭션 안에서 다 꺼내둬야 한다
 *   - 화면 요구가 바뀔 때마다 엔티티를 고치는 상황을 막는다
 *
 * record 를 쓴 이유는 값을 담아 전달하기만 하고 바뀌지 않는 객체이기 때문이다.
 */
public record PostListResponse(
        Long postId,
        String content,
        LocalDateTime eatenDate,
        String writerNickname,
        String placeName,
        String thumbnailPath   // 대표 사진의 상대 경로. 사진이 없으면 null
) {

    /**
     * 엔티티를 DTO 로 변환한다. 반드시 트랜잭션 안에서 호출한다.
     *
     * thumbnailPath 를 밖에서 받는 이유:
     *   사진은 게시물 하나에 여러 장 붙는 컬렉션이라 게시물과 함께 조회하면
     *   행이 중복된다. 그래서 사진은 따로 조회해 Service 에서 짝지어 넘겨준다.
     */
    public static PostListResponse from(Post post, String thumbnailPath) {
        return new PostListResponse(
                post.getPostId(),
                post.getContent(),
                post.getEatenDate(),
                post.getMember().getNickname(),
                // 장소는 없을 수 있다. NULL 을 허용한 컬럼이므로 여기서 반드시 확인한다
                post.getPlace() != null ? post.getPlace().getName() : null,
                thumbnailPath
        );
    }
}
