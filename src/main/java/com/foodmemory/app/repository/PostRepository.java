package com.foodmemory.app.repository;

import com.foodmemory.app.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    /**
     * 작성자를 한 번에 같이 가져오는 조회.
     *
     * join fetch 는 JPQL 문법이다. "조인해서 가져온 것을 엔티티에 채워 넣어라" 는 뜻이다.
     * 그냥 join 만 쓰면 조인은 하되 member 는 채워지지 않아 LAZY 로딩이 다시 일어난다.
     *
     * 이걸 쓰면 findAll() 이 일으키는 N+1 문제가 사라진다.
     * findAll() 은 게시물 목록 1번 + 회원 조회 N번이지만, 이건 1번으로 끝난다.
     *
     * 참고: Post 는 대문자다. 테이블 post 가 아니라 엔티티 클래스 Post 를 가리킨다.
     *      JPQL 은 테이블이 아니라 객체를 대상으로 쓰는 쿼리다.
     */
    @Query("""
            select p from Post p
            join fetch p.member
            left join fetch p.restaurant
            order by p.eatenDate desc
            """)
    List<Post> findAllWithMember();
}
