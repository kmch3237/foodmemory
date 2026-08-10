package com.foodmemory.app.service;

import com.foodmemory.app.common.FileStorage;
import com.foodmemory.app.dto.PostListResponse;
import com.foodmemory.app.entity.Member;
import com.foodmemory.app.entity.Photo;
import com.foodmemory.app.entity.Post;
import com.foodmemory.app.repository.MemberRepository;
import com.foodmemory.app.repository.PhotoRepository;
import com.foodmemory.app.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private final PostRepository postRepository;
    private final PhotoRepository photoRepository;
    private final MemberRepository memberRepository;
    private final FileStorage fileStorage;

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
    public List<PostListResponse> getGallery() {
        // 쿼리 1 — 게시물 + 작성자 + 식당
        List<Post> posts = postRepository.findAllWithMember();
        if (posts.isEmpty()) {
            return List.of();
        }

        List<Long> postIds = posts.stream().map(Post::getPostId).toList();

        // 쿼리 2 — 사진을 한 번에 가져와 게시물별로 묶는다.
        // 게시물마다 사진을 조회하면 게시물 수만큼 쿼리가 늘어난다(N+1).
        Map<Long, List<Photo>> photosByPost = photoRepository.findByPostIds(postIds)
                .stream()
                .collect(Collectors.groupingBy(photo -> photo.getPost().getPostId()));

        return posts.stream()
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
    public Long upload(List<MultipartFile> photos, String content, LocalDateTime eatenDate) {
        if (photos == null || photos.isEmpty() || photos.stream().allMatch(MultipartFile::isEmpty)) {
            throw new IllegalArgumentException("사진을 한 장 이상 올려주세요.");
        }

        // TODO 로그인 붙이기 전까지의 임시 처리.
        //      소셜 로그인을 붙이면 현재 로그인한 회원으로 교체한다.
        Member writer = memberRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("회원이 없습니다. 먼저 회원을 만들어 주세요."));

        // 식당은 아직 지도 API 를 붙이지 않아 null 로 둔다.
        Post post = postRepository.save(Post.create(writer, null, content, eatenDate));

        for (MultipartFile file : photos) {
            if (file.isEmpty()) {
                continue;
            }
            String storedPath = fileStorage.store(file);        // 디스크에 저장하고 경로를 받는다
            photoRepository.save(Photo.create(post, storedPath)); // DB 에는 경로만 저장한다
        }

        return post.getPostId();
    }
}
