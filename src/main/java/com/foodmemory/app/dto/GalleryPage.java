package com.foodmemory.app.dto;

import java.util.List;

/**
 * 갤러리 한 페이지 분량.
 *
 * 목록만 돌려주면 화면이 "더 가져올 게 남았는지" 를 알 수 없다.
 * 그것을 모르면 스크롤이 끝에 닿을 때마다 서버에 계속 물어보게 되고,
 * 마지막 페이지에서도 요청이 멈추지 않는다. 그래서 목록과 함께 알려준다.
 *
 * @param posts    이 페이지의 게시물들
 * @param hasNext  다음 페이지가 남아 있는지
 * @param nextPage 다음에 요청할 페이지 번호. hasNext 가 false 면 의미 없다
 */
public record GalleryPage(
        List<PostListResponse> posts,
        boolean hasNext,
        int nextPage
) {

    /** 게시물이 하나도 없을 때 쓰는 빈 페이지. */
    public static GalleryPage empty(int page) {
        return new GalleryPage(List.of(), false, page);
    }
}
