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

import java.time.LocalDateTime;

/**
 * 게시물 — post 테이블과 매핑된다. 한 번의 식사 기록이다.
 */
@Entity
@Table(name = "post")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long postId;

    /**
     * 작성자.
     *
     * ManyToOne = "여러 게시물(Many)이 한 회원(One)에게 속한다".
     *             내 쪽이 Many 라서 ManyToOne 이다.
     *
     * fetch = LAZY 를 반드시 붙인다.
     *   @ManyToOne 의 기본값은 EAGER 라서, 붙이지 않으면 게시물을 조회할 때마다
     *   쓰지도 않을 회원 정보를 매번 같이 조회한다.
     *   목록 20건을 가져오면 회원 조회 쿼리가 20번 더 나간다(N+1 문제).
     *
     * @JoinColumn(name = "member_id")
     *   내 테이블(post)의 어느 컬럼이 FK 인지를 알려준다.
     *   nullable = false 는 DDL 의 NOT NULL 과 대응된다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /**
     * 먹은 장소. 없을 수 있다.
     *
     * nullable 을 적지 않으면 기본값이 true(NULL 허용)다.
     * 집에서 해먹은 음식, 좌표가 없는 사진, 폐업해서 찾지 못하는 가게를
     * 등록할 수 있어야 하므로 NULL 을 허용한다.
     *
     * 자바에서는 이 값이 null 일 수 있다는 뜻이므로,
     * post.getRestaurant().getName() 을 바로 부르면 NullPointerException 이 난다.
     * 화면에서 다룰 때 null 검사가 필요하다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id")
    private Restaurant restaurant;

    @Column(length = 500)
    private String content;

    /**
     * 먹은 시각. 갤러리 정렬 기준이다.
     * created_at 과 다르다. 3년 전 사진을 오늘 올릴 수 있다.
     */
    @Column(nullable = false)
    private LocalDateTime eatenDate;

    /**
     * 공개 여부. MySQL 의 BOOLEAN(=TINYINT(1)) 과 매핑된다.
     *
     * Boolean 이 아니라 boolean 인 이유:
     *   NOT NULL DEFAULT FALSE 라서 값이 없는 상태가 존재하지 않는다.
     *   null 이 될 수 없는 값은 원시 타입으로 두는 편이 안전하다.
     */
    @Column(nullable = false)
    private boolean isPublic;

    /**
     * 게시물을 작성한다.
     *
     * restaurant 는 null 을 허용한다. 집에서 먹었거나 좌표가 없는 사진일 수 있다.
     * isPublic 은 항상 false 로 시작한다. MVP 에는 공유 기능이 없고,
     * 실수로 공개되는 것을 막는 안전한 기본값이다.
     */
    public static Post create(Member member, Restaurant restaurant,
                              String content, LocalDateTime eatenDate) {
        Post post = new Post();
        post.member = member;
        post.restaurant = restaurant;
        post.content = content;
        post.eatenDate = eatenDate;
        post.isPublic = false;
        return post;
    }

    /**
     * 먹은 장소를 지정한다.
     *
     * setter 를 열지 않고 이런 메서드를 두는 이유는 '무엇을 하는 변경인지' 가
     * 코드에 남기 때문이다. setRestaurant 보다 assignRestaurant 가 의도를 드러낸다.
     *
     * 이 메서드로 값을 바꾸면 UPDATE 문을 직접 쓰지 않아도 DB 에 반영된다.
     * 영속성 컨텍스트가 트랜잭션이 끝날 때 값이 바뀐 것을 감지해 UPDATE 를 만들어 보낸다.
     * 이를 변경 감지(더티 체킹)라고 한다.
     */
    public void assignRestaurant(Restaurant restaurant) {
        this.restaurant = restaurant;
    }
}
