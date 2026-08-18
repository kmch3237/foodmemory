package com.foodmemory.app.service;

import com.foodmemory.app.dto.GalleryPage;
import com.foodmemory.app.dto.PostDetailResponse;
import com.foodmemory.app.dto.PostEditForm;
import com.foodmemory.app.dto.PlaceSearchResult;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

public interface PostService {

    /**
     * 내 기록을 먹은 날짜 최신순으로 한 페이지씩 가져온다.
     *
     * 예전에는 조건 없이 전부 가져왔다. 회원이 한 명이라 드러나지 않았을 뿐,
     * 로그인하지 않은 사람에게도 모든 회원의 기록이 보이는 상태였다.
     */
    GalleryPage getMyGallery(Long memberId, int page);

    /**
     * 공유 공간의 기록을 가져온다. 참여자가 아니면 거부한다.
     *
     * 작성자를 가리지 않는다. 공간은 여러 사람의 기록이 함께 쌓이는 곳이다.
     */
    GalleryPage getSpaceGallery(Long spaceId, Long memberId, int page);

    /**
     * 게시물 한 건을 사진 전체와 함께 가져온다.
     *
     * 볼 수 있는 사람인지 확인한다.
     *   개인 기록 → 작성자 본인만
     *   공간 기록 → 그 공간의 참여자만
     */
    PostDetailResponse getDetail(Long postId, Long loginMemberId);

    /**
     * 게시물에 연결할 장소 후보를 찾는다.
     *
     * keyword 가 있으면 이름으로 찾고, 없으면 사진 좌표로 주변을 찾는다.
     * 좌표도 없고 검색어도 없으면 빈 결과를 돌려준다. 예외를 던지지 않는 이유:
     *   좌표가 없는 것은 오류가 아니라 흔한 상황이다.
     *   iOS Safari 는 업로드할 때 GPS 를 지우고, 메신저를 거친 사진도 마찬가지다.
     *   화면이 검색창을 보여줄 수 있어야 하므로 결과로 돌려준다.
     */
    PlaceSearchResult findPlaceCandidates(Long postId, String keyword);

    /**
     * 후보 중 하나를 골라 게시물에 연결한다.
     *
     * keyword 를 함께 받는 이유:
     *   화면이 보낸 placeId 가 실제 검색 결과에 있던 것인지 서버가 다시 확인해야 한다.
     *   그러려면 사용자가 봤던 것과 같은 검색을 한 번 더 돌려야 하고, 그 조건이 keyword 다.
     *   확인 없이 화면 값을 그대로 저장하면 위조된 가게 정보가 장소 테이블에 들어간다.
     */
    void assignPlace(Long postId, String kakaoPlaceId, String keyword, Long loginMemberId);

    /**
     * 사진과 함께 게시물을 등록한다.
     *
     * @param spaceId 올릴 공간. null 이면 나만 보는 개인 기록이 된다.
     *                값이 있으면 그 공간의 참여자인지 확인한다
     */
    Long upload(List<MultipartFile> photos, String content, LocalDateTime eatenDate,
                Long writerId, Long spaceId);

    /**
     * 이 기록을 볼 수 있는 사람인지 확인한다. 볼 수 없으면 예외를 던진다.
     *
     * 밖으로 꺼낸 이유:
     *   댓글도 같은 규칙을 따라야 한다. 볼 수 없는 기록에 댓글을 달거나
     *   댓글 목록을 읽을 수 있으면, 기록 자체를 감춘 의미가 없어진다.
     *   같은 규칙을 CommentService 에 다시 적으면 둘 중 하나만 고치는 실수가 생긴다.
     *   공개 범위의 판단은 게시물의 몫이므로 여기에 두고 빌려 쓴다.
     */
    void requireCanView(Long postId, Long loginMemberId);

    /**
     * 수정 폼에 채워 넣을 지금 값을 가져온다. 작성자 본인만 열 수 있다.
     *
     * 조회인데도 권한을 확인하는 이유:
     *   수정 화면은 '고칠 수 있는 사람' 에게만 의미가 있다.
     *   남의 기록에서 이 화면이 열리면, 폼을 채우고 저장을 눌렀을 때
     *   그제서야 거부당한다. 열 수 없는 문을 보여주고 손잡이를 돌리게 하는 셈이다.
     *   들어오는 입구에서 막는 편이 낫다.
     */
    PostEditForm getEditForm(Long postId, Long loginMemberId);

    /**
     * 기록의 코멘트·먹은 날짜·공간을 고친다. 작성자 본인만 고칠 수 있다.
     *
     * 사진은 여기서 다루지 않는다. 잘못 올렸으면 지우고 다시 올린다.
     * 장소는 assignPlace 가 따로 맡는다. 고르는 화면이 별도로 있기 때문이다.
     *
     * @param spaceId 옮겨 갈 공간. null 이면 개인 기록으로 되돌린다.
     *                값이 있으면 그 공간의 참여자인지 확인한다
     */
    void update(Long postId, String content, LocalDateTime eatenDate,
                Long spaceId, Long loginMemberId);

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
