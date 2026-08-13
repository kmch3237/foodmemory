package com.foodmemory.app.service;

import com.foodmemory.app.dto.GalleryPage;
import com.foodmemory.app.dto.NearbyPlace;
import com.foodmemory.app.dto.PostDetailResponse;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

public interface PostService {

    /** 갤러리에 뿌릴 게시물을 먹은 날짜 최신순으로 한 페이지씩 가져온다. */
    GalleryPage getGallery(int page);

    /** 게시물 한 건을 사진 전체와 함께 가져온다. */
    PostDetailResponse getDetail(Long postId);

    /** 게시물 사진의 좌표로 주변 음식점 후보를 찾는다. */
    List<NearbyPlace> findNearbyPlaces(Long postId);

    /** 후보 중 하나를 골라 게시물의 식당으로 지정한다. */
    void assignRestaurant(Long postId, String placeId, Long loginMemberId);

    /** 사진과 함께 게시물을 등록한다. */
    Long upload(List<MultipartFile> photos, String content, LocalDateTime eatenDate, Long writerId);

    /**
     * 게시물을 지운다. 작성자 본인만 지울 수 있다.
     *
     * loginMemberId 를 받는 이유:
     *   "누가 지우려 하는가" 를 서비스가 알아야 판단할 수 있다.
     *   컨트롤러에서 확인하고 넘기면, 새 컨트롤러를 만들 때 그 확인을 빠뜨릴 수 있다.
     *   판단을 서비스 안에 두면 어느 경로로 들어오든 같은 규칙이 적용된다.
     */
    void delete(Long postId, Long loginMemberId);
}
