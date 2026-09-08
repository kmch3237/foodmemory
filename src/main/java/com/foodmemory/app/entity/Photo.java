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

    /**
     * 목록에서 쓰는 작은 사본의 상대 경로. 예) 2026/07/abc123_thumb.jpg
     *
     * NULL 을 허용하는 이유:
     *   - 이 칸이 생기기 전에 올라온 사진들은 사본이 없다
     *   - 자바가 못 읽는 형식(HEIC)이나 일시적인 실패로 못 만들 수 있다
     *   원본을 대신 보여주면 화면은 멀쩡하므로, 없는 것을 오류로 다루지 않는다.
     */
    @Column(length = 500)
    private String thumbPath;

    public static Photo create(Post post, String filePath, String thumbPath) {
        Photo photo = new Photo();
        photo.post = post;
        photo.filePath = filePath;
        photo.thumbPath = thumbPath;
        return photo;
    }

    /**
     * 목록에 보여줄 경로. 사본이 있으면 사본을, 없으면 원본을 준다.
     *
     * 이 판단을 엔티티에 두는 이유:
     *   화면·서비스 여러 곳에서 같은 조건문을 반복하면 한 곳만 고치고 놓치기 쉽다.
     *   "무엇을 보여줄지" 는 사진 자신이 제일 잘 안다.
     */
    public String getDisplayPath() {
        return thumbPath != null ? thumbPath : filePath;
    }

    /** 나중에 사본을 만들어 붙일 때 쓴다. 이미 있으면 덮어쓰지 않는다. */
    public void attachThumbnail(String thumbPath) {
        if (this.thumbPath == null) {
            this.thumbPath = thumbPath;
        }
    }
}
