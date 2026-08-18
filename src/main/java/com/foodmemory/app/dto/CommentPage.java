package com.foodmemory.app.dto;

import java.util.List;

/**
 * 댓글 한 쪽 분량과, 화면이 쪽 번호를 그리는 데 필요한 값들.
 *
 * Spring 의 Page 객체를 화면에 그대로 넘기지 않는 이유:
 *   Page 에는 정렬 정보, 요청한 Pageable, 전체 건수 등 화면이 쓰지 않는 것이 잔뜩 들어 있다.
 *   화면에서 page.pageable.sort.orderFor(...) 같은 코드를 쓸 수 있게 되면
 *   화면이 Spring Data 의 구조에 묶여 버린다. 나중에 조회 방식을 바꾸기 어려워진다.
 *   필요한 값만 골라 담아 넘긴다.
 *
 * @param page       지금 쪽 번호. 0 부터 시작한다
 * @param totalPages 전체 쪽수. 댓글이 하나도 없으면 0 이다
 */
public record CommentPage(
        List<CommentResponse> comments,
        int page,
        int totalPages,
        long totalCount
) {

    /**
     * 화면에 보여줄 쪽 번호. 사람은 1부터 센다.
     *
     * 0 부터 세는 것은 컴퓨터 쪽 사정이라 주소(?commentPage=0)에는 그대로 두고,
     * 눈에 보이는 자리에서만 1을 더한다.
     */
    public int displayPage() {
        return page + 1;
    }

    public boolean hasPrev() {
        return page > 0;
    }

    public boolean hasNext() {
        return page + 1 < totalPages;
    }

    public boolean isEmpty() {
        return comments.isEmpty();
    }
}
