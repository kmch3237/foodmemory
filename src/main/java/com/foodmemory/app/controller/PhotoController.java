package com.foodmemory.app.controller;

import com.foodmemory.app.auth.Login;
import com.foodmemory.app.auth.LoginMember;
import com.foodmemory.app.common.FileStorage;
import com.foodmemory.app.common.NotFoundException;
import com.foodmemory.app.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * 사진 파일을 내보낸다. 볼 권한이 있는 사람에게만.
 *
 * ── 왜 만들었나 ──
 *
 * 전에는 /uploads/2026/08/3f8c….jpg 처럼 파일 경로가 그대로 주소였고,
 * 로그인 검사도 없었다. 주소만 알면 누구나 남의 사진을 받을 수 있었다.
 *
 * 파일 이름이 UUID 라 찍어 맞히기는 어렵다. 하지만 문제는 추측이 아니었다.
 * 권한이 '주소를 아느냐' 에 걸려 있으면 관계가 끝나도 접근이 끝나지 않는다.
 * 방에서 나간 사람도, 탈퇴한 사람도, 메신저로 주소를 건네받은 사람도
 * 그 주소가 살아 있는 한 영원히 볼 수 있다. 되돌릴 방법도 없다.
 *
 * 그래서 주소를 번호로 바꾸고(/photos/12), 요청이 올 때마다 물어보게 했다.
 * 이제 방에서 나가면 다음 요청부터 막힌다.
 *
 * ── 느려지지 않나 ──
 *
 * 확인은 인덱스가 걸린 조회 한 번이다(PhotoRepository.findWithPostById).
 * 게다가 아래에서 붙이는 private 캐시 덕에 같은 사진을 두 번째 볼 때는
 * 요청 자체가 서버에 오지 않는다. 사실상 사진 한 장당 평생 한 번이다.
 */
@Controller
@RequiredArgsConstructor
public class PhotoController {

    private final PostService postService;
    private final FileStorage fileStorage;

    /** 원본. 상세 화면에서 크게 볼 때 쓴다. */
    @GetMapping("/photos/{photoId}")
    public ResponseEntity<Resource> original(@PathVariable Long photoId,
                                             @Login LoginMember loginMember) {
        return send(photoId, loginMember, false);
    }

    /**
     * 작은 사본. 목록의 손톱만 한 칸에 쓴다.
     *
     * 사본이 없는 사진(HEIC 처럼 자바가 못 읽는 형식)은 원본이 대신 나간다.
     * 그 판단은 Photo.getDisplayPath() 가 한다.
     */
    @GetMapping("/photos/{photoId}/thumb")
    public ResponseEntity<Resource> thumbnail(@PathVariable Long photoId,
                                              @Login LoginMember loginMember) {
        return send(photoId, loginMember, true);
    }

    /**
     * 권한을 확인하고 파일을 실어 보낸다.
     *
     * loginMember 가 null 이면 서비스가 막는다. 여기서 미리 걸러내지 않는 이유는
     * 막는 규칙이 한 곳에만 있어야 하기 때문이다. 두 곳에 있으면 언젠가 어긋난다.
     * (WebConfig 의 로그인 검사 목록에도 /photos/** 가 있어 보통은 여기까지 오지 않는다.)
     */
    private ResponseEntity<Resource> send(Long photoId, LoginMember loginMember, boolean thumbnail) {
        Long memberId = (loginMember == null) ? null : loginMember.memberId();

        String relativePath = postService.getViewablePhotoPath(photoId, memberId, thumbnail);
        Path file = fileStorage.resolve(relativePath);

        // DB 에는 경로가 있는데 파일이 없는 경우. 백업에서 되살리다 어긋나면 생긴다.
        // 여기서 분명히 404 로 끊지 않으면 0바이트 이미지가 나가 원인을 찾기 어려워진다.
        if (!Files.isReadable(file)) {
            throw new NotFoundException("사진 파일을 찾을 수 없습니다.");
        }

        return ResponseEntity.ok()
                .contentType(contentTypeOf(file))
                /*
                 * private — "브라우저 너만 저장해라. 중간의 공용 캐시는 저장하지 마라".
                 *
                 * 사람마다 볼 수 있는 사진이 다르므로 여럿이 함께 쓰는 캐시에 담기면
                 * 남의 사진이 엉뚱한 사람에게 갈 수 있다. 반대로 브라우저 안에서는
                 * 그 사람 것만 모이므로 안전하고, 덕분에 두 번째 조회는 서버에 오지 않는다.
                 *
                 * immutable — "이 주소의 내용은 절대 안 바뀐다".
                 * 파일 이름이 UUID 라 같은 이름이 다시 쓰이는 일이 없어 참말이 된다.
                 * 새로고침해도 브라우저가 서버에 다시 묻지 않는다.
                 */
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable())
                .body(new FileSystemResource(file));
    }

    /**
     * 파일 확장자로 형식을 정한다.
     *
     * 못 알아보면 octet-stream(그냥 바이트 덩어리)으로 보낸다.
     * 그러면 브라우저가 그림으로 그리지 않고 내려받기로 처리하는데,
     * 엉뚱한 형식이라고 우기는 것보다는 낫다.
     */
    private MediaType contentTypeOf(Path file) {
        return MediaTypeFactory.getMediaType(file.getFileName().toString())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
    }
}
