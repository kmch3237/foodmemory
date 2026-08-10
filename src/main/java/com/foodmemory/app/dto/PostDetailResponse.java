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
                post.getMember().getNickname(),
                post.getRestaurant() != null ? post.getRestaurant().getName() : null,
                post.getRestaurant() != null ? post.getRestaurant().getAddress() : null,
                photoPaths
        );
    }
}
