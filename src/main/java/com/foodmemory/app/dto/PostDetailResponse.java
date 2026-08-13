package com.foodmemory.app.dto;

import com.foodmemory.app.entity.Post;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 게시물 상세 화면에 내보낼 데이터.
 *
 * 목록용(PostListResponse)과 따로 만든 이유:
 *   목록은 대표 사진 한 장만 필요하지만 상세는 사진 전부가 필요하다.
 *   화면마다 필요한 데이터가 다르므로 DTO 도 화면 단위로 나눈다.
 *   하나로 합쳐 쓰면 목록에서도 사진을 전부 조회하게 되어 낭비가 생긴다.
 */
public record PostDetailResponse(
        Long postId,
        String content,
        LocalDateTime eatenDate,

        /**
         * 작성자의 회원 번호.
         *
         * 닉네임과 별도로 담는 이유:
         *   화면에서 "이 글이 내 글인가" 를 판단하려면 번호로 비교해야 한다.
         *   닉네임은 중복될 수 있어서(UNIQUE 를 걸지 않았다) 남의 글에
         *   삭제 버튼이 뜨는 일이 생긴다.
         *
         * 화면에 보이는 값이 아니라 비교용 값이다.
         * 그리고 이 값만 믿고 삭제를 허용하지는 않는다. 화면은 버튼을 감출 뿐이고,
         * 실제 판단은 서버가 다시 한다. 버튼이 없어도 주소로 직접 요청할 수 있기 때문이다.
         */
        Long writerId,

        String writerNickname,
        String restaurantName,
        String restaurantAddress,
        List<String> photoPaths      // 올린 순서대로. 비어 있을 수 있다
) {

    public static PostDetailResponse from(Post post, List<String> photoPaths) {
        return new PostDetailResponse(
                post.getPostId(),
                post.getContent(),
                post.getEatenDate(),
                post.getMember().getMemberId(),
                post.getMember().getNickname(),
                post.getRestaurant() != null ? post.getRestaurant().getName() : null,
                post.getRestaurant() != null ? post.getRestaurant().getAddress() : null,
                photoPaths
        );
    }
}
