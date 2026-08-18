package com.foodmemory.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 댓글 — comment 테이블과 매핑된다.
 *
 * 게시물 하나에 댓글이 여러 개 달리고, 한 회원이 댓글을 여러 개 쓴다.
 * 그래서 댓글 쪽에서 보면 게시물도 회원도 @ManyToOne 이다.
 */
@Entity
@Table(name = "comment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long commentId;

    /**
     * 달린 게시물.
     *
     * Post 에 @OneToMany List&lt;Comment&gt; 를 두지 않은 이유:
     *   그렇게 하면 게시물을 꺼낼 때마다 댓글 목록이 따라다니게 되고,
     *   댓글이 필요 없는 갤러리 화면에서도 조회가 일어나기 쉽다.
     *   댓글은 상세 화면에서만 필요하므로 그때 따로 조회하는 편이 낫다.
     *
     * 양쪽에 다 걸지 않아도 관계는 성립한다. FK 는 comment 테이블에만 있으면 된다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    /** 작성자. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /**
     * 댓글 내용.
     *
     * nullable 을 적지 않으면 기본값이 NULL 허용이라, DDL 의 NOT NULL 과 어긋난다.
     * ddl-auto: validate 가 그 차이를 잡아내 애플리케이션이 아예 뜨지 않는다.
     * length 도 마찬가지로 DDL 의 300 과 맞춰야 한다.
     */
    @Column(nullable = false, length = 300)
    private String content;

    public static Comment create(Post post, Member member, String content) {
        Comment comment = new Comment();
        comment.post = post;
        comment.member = member;
        comment.content = content;
        return comment;
    }

    /**
     * 댓글 내용을 고친다.
     *
     * Post.update 와 같이 UPDATE 문을 만들지 않는다.
     * 트랜잭션이 끝날 때 영속성 컨텍스트가 값이 바뀐 것을 감지해 UPDATE 를 보낸다.
     */
    public void updateContent(String content) {
        this.content = content;
    }

    /** 이 댓글을 쓴 사람인지 확인할 때 쓴다. */
    public boolean isWrittenBy(Long memberId) {
        return member.getMemberId().equals(memberId);
    }
}
