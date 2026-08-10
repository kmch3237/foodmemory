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
 * 사진 — photo 테이블과 매핑된다.
 *
 * 게시물에 여러 장이 붙는다. 식당은 게시물이 알고 있으므로 사진은 알 필요가 없다.
 *   사진 → 게시물 → 식당
 */
@Entity
@Table(name = "photo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Photo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long photoId;

    /**
     * 속한 게시물.
     *
     * 한 게시물에 사진이 여러 장 붙으므로, 사진 입장에서 자기는 Many 다.
     * 따라서 Post 와 마찬가지로 @ManyToOne 이 된다.
     *   사진(Many) : 게시물(One)
     *
     * fetch = LAZY 는 예외 없이 붙인다.
     * nullable = false — 게시물 없는 사진은 존재할 수 없다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    /**
     * 도메인과 버킷을 제외한 상대 경로. 예) 2026/07/abc123.jpg
     *
     * 전체 URL 을 저장하지 않는 이유는 버킷 변경이나 CDN 도입으로 앞부분이 바뀔 때
     * 사진 수만 장의 값을 전부 고쳐야 하기 때문이다.
     * 앞부분은 설정 파일에 한 줄로 두고 화면에서 합친다.
     */
    @Column(nullable = false, length = 500)
    private String filePath;

    public static Photo create(Post post, String filePath) {
        Photo photo = new Photo();
        photo.post = post;
        photo.filePath = filePath;
        return photo;
    }
}
