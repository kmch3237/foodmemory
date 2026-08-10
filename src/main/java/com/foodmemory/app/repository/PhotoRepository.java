package com.foodmemory.app.repository;

import com.foodmemory.app.entity.Photo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

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
}
