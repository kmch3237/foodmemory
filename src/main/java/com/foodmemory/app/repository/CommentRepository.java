package com.foodmemory.app.repository;

import com.foodmemory.app.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * 한 게시물의 댓글을 오래된 순으로 한 페이지씩 가져온다.
     *
     * join fetch 를 쓰는 이유:
     *   댓글마다 작성자 닉네임을 화면에 찍어야 한다. member 는 LAZY 라서
     *   그냥 조회하면 목록을 그리는 동안 댓글 수만큼 회원 조회 쿼리가 더 나간다.
     *   댓글 5개면 쿼리가 6번이다. 이것이 N+1 문제다.
     *   처음부터 조인해서 같이 가져오면 쿼리 한 번으로 끝난다.
     *
     * countQuery 를 따로 적는 이유:
     *   Page 는 전체가 몇 건인지 세는 쿼리를 한 번 더 보낸다.
     *   그 쿼리에 join fetch 가 그대로 들어가면 "세기만 하면 되는데 왜 가져오냐" 며
     *   Hibernate 가 오류를 낸다. 세는 쿼리는 조인 없이 따로 적어준다.
     *
     * 갤러리는 Slice 를 쓰는데 여기는 Page 인 이유:
     *   무한 스크롤은 "다음이 있느냐" 만 알면 되지만,
     *   번호를 눌러 오가는 페이징은 "전체 몇 쪽인지" 를 알아야 한다.
     *   그래서 세는 쿼리 한 번을 더 내는 대신 총 쪽수를 얻는다.
     *
     * 오래된 순인 이유:
     *   대화는 위에서 아래로 읽는다. 최신순으로 두면 답이 질문보다 위에 오게 된다.
     *   갤러리(최신순)와 정렬이 반대인 것은 성격이 다르기 때문이다.
     */
    @Query(value = """
            select c from Comment c
            join fetch c.member
            where c.post.postId = :postId
            order by c.commentId asc
            """,
            countQuery = """
            select count(c) from Comment c
            where c.post.postId = :postId
            """)
    Page<Comment> findPageByPostId(@Param("postId") Long postId, Pageable pageable);

    /** 댓글이 몇 개인지. 등록 직후 마지막 쪽으로 보낼 때 쓴다. */
    long countByPostPostId(Long postId);

    /** 게시물을 지울 때 딸린 댓글부터 정리하는 데 쓴다. */
    void deleteByPostPostId(Long postId);
}
