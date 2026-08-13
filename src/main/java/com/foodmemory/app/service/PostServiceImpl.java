package com.foodmemory.app.service;

import com.foodmemory.app.common.ExifReader;
import com.foodmemory.app.common.FileStorage;
import com.foodmemory.app.common.PhotoMetadata;
import com.foodmemory.app.common.ForbiddenException;
import com.foodmemory.app.common.NotFoundException;
import com.foodmemory.app.common.KakaoLocalClient;
import com.foodmemory.app.dto.GalleryPage;
import com.foodmemory.app.dto.NearbyPlace;
import com.foodmemory.app.dto.PostDetailResponse;
import com.foodmemory.app.dto.PostListResponse;
import com.foodmemory.app.entity.Member;
import com.foodmemory.app.entity.Photo;
import com.foodmemory.app.entity.Post;
import com.foodmemory.app.entity.Restaurant;
import com.foodmemory.app.repository.RestaurantRepository;
import com.foodmemory.app.repository.MemberRepository;
import com.foodmemory.app.repository.PhotoRepository;
import com.foodmemory.app.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private final PostRepository postRepository;
    private final PhotoRepository photoRepository;
    private final MemberRepository memberRepository;
    private final FileStorage fileStorage;
    private final ExifReader exifReader;
    private final RestaurantRepository restaurantRepository;
    private final KakaoLocalClient kakaoLocalClient;

    /**
     * 한 번에 가져올 게시물 수.
     *
     * 격자가 3열이므로 3의 배수로 두어야 마지막 줄이 어중간하게 비지 않는다.
     * 크게 잡으면 요청 수는 줄지만 첫 화면이 느려지고, 작게 잡으면 그 반대다.
     */
    private static final int PAGE_SIZE = 6;

    /**
     * readOnly = true 는 조회 전용 트랜잭션이라는 표시다.
     * 변경 감지를 위한 스냅샷을 만들지 않아 조금 가볍고, 실수로 데이터를 바꾸는 것도 막아준다.
     *
     * 트랜잭션이 필요한 이유:
     *   DTO 변환 과정에서 post.getMember() 에 접근한다.
     *   open-in-view: false 이므로 트랜잭션이 끝나면 LAZY 로딩을 할 수 없다.
     */
    @Override
    @Transactional(readOnly = true)
    public GalleryPage getGallery(int page) {
        // 쿼리 1 — 게시물 + 작성자 + 식당
        //
        // 정렬은 이미 쿼리의 order by 에 적혀 있으므로 PageRequest 에는 정렬을 주지 않는다.
        // 양쪽에 정렬을 걸면 order by 가 두 번 붙어 어긋난다.
        Slice<Post> slice = postRepository.findAllWithMember(PageRequest.of(page, PAGE_SIZE));
        List<Post> posts = slice.getContent();
        if (posts.isEmpty()) {
            return GalleryPage.empty(page);
        }

        List<Long> postIds = posts.stream().map(Post::getPostId).toList();

        // 쿼리 2 — 사진을 한 번에 가져와 게시물별로 묶는다.
        // 게시물마다 사진을 조회하면 게시물 수만큼 쿼리가 늘어난다(N+1).
        Map<Long, List<Photo>> photosByPost = photoRepository.findByPostIds(postIds)
                .stream()
                .collect(Collectors.groupingBy(photo -> photo.getPost().getPostId()));

        List<PostListResponse> items = posts.stream()
                .map(post -> {
                    List<Photo> photos = photosByPost.get(post.getPostId());
                    // 대표 사진은 photo_id 가 가장 작은 것, 즉 먼저 올린 사진이다.
                    // 쿼리에서 이미 오름차순 정렬했으므로 첫 번째를 쓰면 된다.
                    String thumbnail = (photos == null || photos.isEmpty())
                            ? null
                            : photos.get(0).getFilePath();
                    return PostListResponse.from(post, thumbnail);
                })
                .toList();

        return new GalleryPage(items, slice.hasNext(), page + 1);
    }

    /**
     * 게시물 한 건을 사진 전체와 함께 가져온다.
     *
     * 없는 게시물을 요청하면 예외를 던진다.
     * 여기서 null 을 돌려주면 화면에서 다시 검사해야 하고, 빠뜨리면 엉뚱한 곳에서 터진다.
     * 조회 실패는 조회한 자리에서 드러내는 편이 원인을 찾기 쉽다.
     */
    @Override
    @Transactional(readOnly = true)
    public PostDetailResponse getDetail(Long postId) {
        // 쿼리 1 — 게시물 + 작성자 + 식당
        Post post = postRepository.findDetailById(postId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 게시물입니다."));

        // 쿼리 2 — 사진 전체. 목록과 달리 상세는 전부 필요하다.
        List<String> photoPaths = photoRepository
                .findByPostPostIdOrderByPhotoIdAsc(postId)
                .stream()
                .map(Photo::getFilePath)
                .toList();

        return PostDetailResponse.from(post, photoPaths);
    }

    /**
     * 게시물에 붙은 첫 사진의 좌표로 주변 음식점을 찾는다.
     *
     * 좌표를 DB 에 따로 저장해두지 않았지만 문제되지 않는다.
     * 사진 파일 자체가 원본이므로 필요할 때 다시 읽으면 된다.
     * 게시물에 좌표를 복사해두면 사진을 바꿨을 때 어긋나게 된다.
     */
    @Override
    @Transactional(readOnly = true)
    public List<NearbyPlace> findNearbyPlaces(Long postId) {
        if (!kakaoLocalClient.isConfigured()) {
            throw new IllegalStateException("지도 API 키가 설정되지 않았습니다.");
        }

        List<Photo> photos = photoRepository.findByPostPostIdOrderByPhotoIdAsc(postId);
        if (photos.isEmpty()) {
            throw new IllegalArgumentException("사진이 없어 위치를 찾을 수 없습니다.");
        }

        PhotoMetadata metadata = exifReader.read(fileStorage.resolve(photos.get(0).getFilePath()));
        if (!metadata.hasLocation()) {
            throw new IllegalArgumentException(
                    "사진에 위치 정보가 없습니다. 메신저를 거친 사진이거나 위치 저장이 꺼져 있었을 수 있습니다.");
        }

        return kakaoLocalClient.searchRestaurants(metadata.latitude(), metadata.longitude());
    }

    /**
     * 사용자가 고른 장소를 게시물의 식당으로 지정한다.
     *
     * 이미 저장된 식당이면 다시 만들지 않고 그것을 쓴다.
     * 같은 가게에 여러 사람이 다녀와도 식당 테이블에는 한 줄만 있어야
     * '이 집에서 먹은 것들 모아보기' 가 성립한다.
     */
    @Override
    @Transactional
    public void assignRestaurant(Long postId, String placeId, Long loginMemberId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 게시물입니다."));

        requireOwner(post, loginMemberId);

        NearbyPlace selected = findNearbyPlaces(postId).stream()
                .filter(place -> place.placeId().equals(placeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("선택한 장소를 찾을 수 없습니다."));

        Restaurant restaurant = restaurantRepository.findByPlaceId(selected.placeId())
                .orElseGet(() -> restaurantRepository.save(Restaurant.from(
                        selected.placeId(),
                        selected.name(),
                        selected.address(),
                        selected.latitude(),
                        selected.longitude())));

        // UPDATE 문을 직접 쓰지 않는다.
        // 트랜잭션이 끝날 때 영속성 컨텍스트가 값이 바뀐 것을 감지해 UPDATE 를 만들어 보낸다.
        post.assignRestaurant(restaurant);
    }

    /**
     * 게시물과 사진을 함께 저장한다.
     *
     * @Transactional 이 붙어 있으므로 중간에 예외가 나면 게시물과 사진 저장이 모두 취소된다.
     * 다만 이미 디스크에 써진 파일은 롤백되지 않는다. 트랜잭션은 DB 만 되돌린다.
     * 지금은 그대로 두고, 나중에 주인 없는 파일을 정리하는 작업을 따로 만든다.
     */
    @Override
    @Transactional
    public Long upload(List<MultipartFile> photos, String content, LocalDateTime eatenDate, Long writerId) {
        if (photos == null || photos.isEmpty() || photos.stream().allMatch(MultipartFile::isEmpty)) {
            throw new IllegalArgumentException("사진을 한 장 이상 올려주세요.");
        }

        // 첫 번째 사진에서 촬영 시각과 좌표를 읽는다.
        // 한 게시물의 사진들은 같은 자리에서 찍은 것으로 보므로 대표 한 장이면 충분하다.
        MultipartFile first = photos.stream().filter(f -> !f.isEmpty()).findFirst().orElseThrow();
        PhotoMetadata metadata = exifReader.read(first);

        // 사용자가 날짜를 비워두면 사진의 촬영 시각을 쓴다.
        // 그것마저 없으면(EXIF 가 지워진 사진 등) 현재 시각으로 둔다.
        LocalDateTime resolvedEatenDate = eatenDate;
        if (resolvedEatenDate == null) {
            resolvedEatenDate = metadata.hasTakenAt() ? metadata.takenAt() : LocalDateTime.now();
        }

        if (metadata.hasLocation()) {
            log.info("사진에서 좌표를 읽었습니다: {}, {}", metadata.latitude(), metadata.longitude());
        }

        // 로그인한 회원이 작성자가 된다.
        // 인터셉터가 /posts 를 막고 있으므로 여기까지 왔다면 writerId 는 null 이 아니다.
        // 그래도 조회에 실패하면 예외를 던진다. 세션이 남아 있는데 회원이 지워진 경우다.
        Member writer = memberRepository.findById(writerId)
                .orElseThrow(() -> new IllegalStateException("로그인 정보가 올바르지 않습니다. 다시 로그인해주세요."));

        // 폼에서 코멘트를 비워두면 null 이 아니라 빈 문자열("")이 넘어온다.
        // MySQL 에서는 ''와 NULL 이 서로 다른 값이라, 그대로 저장하면
        // "코멘트 없음" 을 판단할 때 두 경우를 모두 검사해야 한다.
        // 저장 전에 한 번 정리해서 DB 에는 NULL 만 들어가게 한다.
        String normalizedContent = (content == null || content.isBlank()) ? null : content.trim();

        // 식당은 아직 지도 API 를 붙이지 않아 null 로 둔다.
        Post post = postRepository.save(Post.create(writer, null, normalizedContent, resolvedEatenDate));

        for (MultipartFile file : photos) {
            if (file.isEmpty()) {
                continue;
            }
            String storedPath = fileStorage.store(file);        // 디스크에 저장하고 경로를 받는다
            photoRepository.save(Photo.create(post, storedPath)); // DB 에는 경로만 저장한다
        }

        return post.getPostId();
    }

    /**
     * 게시물을 지운다.
     *
     * 지우는 순서가 정해져 있다. 사진 → 게시물이다.
     * photo 가 post 를 FK 로 참조하므로, 게시물을 먼저 지우려 하면 DB 가 거부한다.
     * 부모를 지우려면 자식부터 정리해야 한다.
     */
    @Override
    @Transactional
    public void delete(Long postId, Long loginMemberId) {
        Post post = postRepository.findDetailById(postId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 게시물입니다."));

        requireOwner(post, loginMemberId);

        List<Photo> photos = photoRepository.findByPostPostIdOrderByPhotoIdAsc(postId);

        // 파일 경로를 미리 챙겨둔다. 행을 지운 뒤에는 어떤 파일이었는지 알 수 없다.
        List<String> filePaths = photos.stream().map(Photo::getFilePath).toList();

        photoRepository.deleteAll(photos);
        postRepository.delete(post);

        deleteFilesAfterCommit(filePaths);

        log.info("게시물 삭제: postId={}, 사진 {}장", postId, filePaths.size());
    }

    /**
     * 실제 파일은 DB 변경이 커밋된 뒤에 지운다.
     *
     * 순서가 중요한 이유:
     *   파일을 먼저 지웠는데 그 뒤 트랜잭션이 실패해 롤백되면, DB 에는 게시물이 그대로 남고
     *   사진 파일만 사라진다. 화면에 깨진 이미지가 뜨고 되살릴 방법이 없다.
     *   반대로 DB 를 먼저 지우고 파일 삭제가 실패하면, 아무도 참조하지 않는 파일이 남을 뿐이다.
     *   눈에 띄지 않고 나중에 정리할 수 있다.
     *
     * 둘 중 하나는 반드시 어긋날 수 있다. 트랜잭션은 DB 만 되돌리기 때문이다.
     * 그렇다면 덜 나쁜 쪽을 고른다.
     *
     * afterCommit 은 커밋이 성공한 뒤에만 불린다. 롤백되면 실행되지 않는다.
     */
    private void deleteFilesAfterCommit(List<String> filePaths) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (String path : filePaths) {
                    fileStorage.delete(path);
                }
            }
        });
    }

    /**
     * 이 게시물을 건드릴 자격이 있는지 확인한다.
     *
     * 화면에서 버튼을 감추는 것만으로는 부족하다.
     * 버튼이 보이지 않아도 주소를 직접 요청하면 그만이기 때문이다.
     * 화면은 편의를 위한 것이고, 판단은 서버가 한다.
     */
    private void requireOwner(Post post, Long loginMemberId) {
        if (loginMemberId == null
                || !post.getMember().getMemberId().equals(loginMemberId)) {
            throw new ForbiddenException("본인이 작성한 기록만 수정하거나 삭제할 수 있습니다.");
        }
    }
}
