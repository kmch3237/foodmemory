package com.foodmemory.app.service;

import com.foodmemory.app.dto.PostDetailResponse;
import com.foodmemory.app.dto.PostListResponse;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

public interface PostService {

    /** 갤러리에 뿌릴 게시물 목록을 먹은 날짜 최신순으로 가져온다. */
    List<PostListResponse> getGallery();

    /** 게시물 한 건을 사진 전체와 함께 가져온다. */
    PostDetailResponse getDetail(Long postId);

    /** 사진과 함께 게시물을 등록한다. */
    Long upload(List<MultipartFile> photos, String content, LocalDateTime eatenDate);
}
