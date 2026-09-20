package com.foodmemory.app.repository;

import com.foodmemory.app.entity.Photo;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PhotoRepository extends JpaRepository<Photo, Long> {

    /**
     * 여러 게시물의 사진을 한 번에 가져온다.
     *
     * 게시물마다 사진을 따로 조회하면 게시물 수만큼 쿼리가 나간다(N+1).
     * 게시물 ID 목록을 한꺼번에 넘겨 쿼리 한 번으로 가져온 뒤,
     * 자바에서 게시물별로 묶는다.
     *
     * 사진은 게시물 하나에 여러 장 붙는 컬렉션이라 join fetch 로 붙이면
     * 게시물 행이 사진 수만큼 중복되는 문제가 생긴다. 그래서 따로 조회한다.
     */
    @Query("select ph from Photo ph where ph.post.postId in :postIds order by ph.photoId asc")
    List<Photo> findByPostIds(@Param("postIds") List<Long> postIds);

    /**
     * 게시물 한 건의 사진을 올린 순서대로 가져온다.
     *
     * 메서드 이름으로 쿼리가 만들어지는 방식이다.
     *   findBy PostPostId       →  where photo.post.post_id = ?
     *   OrderBy PhotoIdAsc      →  order by photo_id asc
     *
     * photo_id 오름차순이 곧 업로드 순서다. 별도의 순서 컬럼을 두지 않은 이유이기도 하다.
     */
    List<Photo> findByPostPostIdOrderByPhotoIdAsc(Long postId);

    /**
     * 사진 한 장을 게시물·작성자·공간까지 한 번에 가져온다.
     *
     * 사진을 내보내기 전에 볼 권한이 있는지 확인하는데, 그 확인이
     * 게시물의 주인(member)과 공간(space)을 본다. 셋 다 LAZY 라 그냥 꺼내면
     * 사진 한 장에 쿼리가 네 번 나간다. 목록 한 페이지가 사진 12장이니 48번이 된다.
     *
     * space 가 left join 인 이유: 개인 기록은 공간이 없다(NULL).
     * 그냥 join 으로 쓰면 개인 기록의 사진이 한 장도 조회되지 않는다.
     */
    @Query("""
            select ph from Photo ph
            join fetch ph.post p
            join fetch p.member
            left join fetch p.space
            where ph.photoId = :photoId
            """)
    Optional<Photo> findWithPostById(@Param("photoId") Long photoId);

    /**
     * 아직 작은 사본이 없는 사진을 가져온다. 뒤늦게 만들어 붙일 때 쓴다.
     *
     * Pageable 을 받는 이유:
     *   사진이 많이 쌓인 뒤에 전부 한꺼번에 꺼내면 메모리가 감당하지 못한다.
     *   운영 서버는 램이 1GB 뿐이다. 한 번에 처리할 만큼만 끊어 가져온다.
     */
    @Query("select ph from Photo ph where ph.thumbPath is null order by ph.photoId asc")
    List<Photo> findByThumbPathIsNull(Pageable pageable);

    /**
     * photoId 가 lastId 보다 큰 사진을 순서대로 가져온다. 전체를 조금씩 끊어 훑을 때 쓴다.
     *
     * 몇 번째 페이지(offset)가 아니라 '마지막으로 본 번호 다음부터' 로 끊는 이유:
     *   훑는 도중에 사진이 올라오거나 지워져도 빠지거나 두 번 보는 사진이 없다.
     */
    List<Photo> findByPhotoIdGreaterThanOrderByPhotoIdAsc(Long lastId, Pageable pageable);
}
