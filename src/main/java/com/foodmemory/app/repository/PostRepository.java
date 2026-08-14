package com.foodmemory.app.repository;

import com.foodmemory.app.entity.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    /**
     * 갤러리 목록을 한 페이지씩 가져온다. 작성자와 장소를 한 번에 가져온다.
     *
     * join fetch 는 "조인해서 가져온 것을 엔티티에 채워 넣어라" 는 뜻이다.
     * 그냥 join 만 쓰면 조인은 하되 채워지지 않아 LAZY 로딩이 다시 일어난다.
     *
     * 장소에는 left 를 붙인다. place_id 는 NULL 을 허용하므로,
     * 내부 조인을 쓰면 집에서 먹은 기록처럼 장소가 없는 게시물이 결과에서 사라진다.
     *
     * 반환 타입이 Page 가 아니라 Slice 인 이유:
     *   Page 는 "전체 몇 건인지" 를 알려주려고 count 쿼리를 한 번 더 보낸다.
     *   무한 스크롤에는 전체 건수가 필요 없다. 필요한 건 "다음이 있느냐" 뿐이다.
     *   Slice 는 요청한 개수보다 한 건 더 읽어보고 그것으로 다음 유무를 판단한다.
     *   쿼리가 하나 줄어든다.
     *
     * 페이징과 join fetch 를 같이 써도 되는 이유:
     *   회원·장소는 게시물당 하나씩(ToOne)이라 조인해도 행이 늘지 않는다.
     *   그래서 DB 에 limit/offset 을 그대로 맡길 수 있다.
     *   사진처럼 여러 건 붙는 컬렉션을 fetch join 하면 행이 뻥튀기돼서
     *   Hibernate 가 전체를 메모리로 읽은 뒤 자르는 사고가 난다. 그래서 사진은 따로 조회한다.
     *
     * 정렬에 postId 를 덧붙인 이유:
     *   먹은 날짜가 똑같은 게시물이 여럿이면 DB 가 그 사이의 순서를 보장하지 않는다.
     *   페이지마다 순서가 달라지면 1페이지에 나온 게시물이 2페이지에 또 나오거나
     *   아예 빠지는 일이 생긴다. 절대 겹치지 않는 값을 뒤에 붙여 순서를 고정한다.
     */
    @Query("""
            select p from Post p
            join fetch p.member
            left join fetch p.place
            where p.member.memberId = :memberId
            order by p.eatenDate desc, p.postId desc
            """)
    Slice<Post> findMyPosts(@Param("memberId") Long memberId, Pageable pageable);

    /**
     * 공유 공간의 게시물 목록.
     *
     * 작성자를 가리지 않는다. 공간에 올린 것은 참여자 전원의 기록이 함께 쌓이는 것이 목적이다.
     * 대신 "그 공간의 참여자인가" 를 Service 가 먼저 확인해야 한다.
     * 이 쿼리 자체는 권한을 검사하지 않으므로, 확인 없이 부르면 남의 공간이 그대로 열린다.
     */
    @Query("""
            select p from Post p
            join fetch p.member
            left join fetch p.place
            where p.space.spaceId = :spaceId
            order by p.eatenDate desc, p.postId desc
            """)
    Slice<Post> findSpacePosts(@Param("spaceId") Long spaceId, Pageable pageable);

    /**
     * 상세 화면용 단건 조회.
     *
     * findById() 를 쓰면 게시물만 가져오고, 작성자·장소·공간은 화면에서 꺼낼 때
     * 쿼리가 한 번씩 더 나간다. 상세 화면은 모두 필요하므로 처음부터 같이 가져온다.
     *
     * space 에 left 를 붙이는 이유는 place 와 같다. 개인 기록은 공간이 없다.
     */
    @Query("""
            select p from Post p
            join fetch p.member
            left join fetch p.place
            left join fetch p.space
            where p.postId = :postId
            """)
    Optional<Post> findDetailById(@Param("postId") Long postId);
}
