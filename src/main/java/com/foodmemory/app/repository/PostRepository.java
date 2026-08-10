package com.foodmemory.app.repository;

import com.foodmemory.app.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    /**
     * 갤러리 목록. 작성자와 식당을 한 번에 가져온다.
     *
     * join fetch 는 "조인해서 가져온 것을 엔티티에 채워 넣어라" 는 뜻이다.
     * 그냥 join 만 쓰면 조인은 하되 채워지지 않아 LAZY 로딩이 다시 일어난다.
     *
     * 식당에는 left 를 붙인다. restaurant_id 는 NULL 을 허용하므로,
     * 내부 조인을 쓰면 집에서 먹은 기록처럼 식당이 없는 게시물이 결과에서 사라진다.
     */
    @Query("""
            select p from Post p
            join fetch p.member
            left join fetch p.restaurant
            order by p.eatenDate desc
            """)
    List<Post> findAllWithMember();

    /**
     * 상세 화면용 단건 조회.
     *
     * findById() 를 쓰면 게시물만 가져오고, 작성자와 식당은 화면에서 꺼낼 때
     * 쿼리가 한 번씩 더 나간다. 상세 화면은 셋 다 필요하므로 처음부터 같이 가져온다.
     */
    @Query("""
            select p from Post p
            join fetch p.member
            left join fetch p.restaurant
            where p.postId = :postId
            """)
    Optional<Post> findDetailById(@Param("postId") Long postId);
}
