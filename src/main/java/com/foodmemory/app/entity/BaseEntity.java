package com.foodmemory.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 모든 엔티티가 공통으로 갖는 시각 정보.
 *
 * 모든 테이블에 created_at / updated_at 을 두기로 했으므로,
 * 네 엔티티에 같은 코드를 반복하는 대신 여기로 뺀다.
 */
@Getter
@MappedSuperclass                                  // 테이블은 만들지 않고 필드만 자식에게 물려준다
@EntityListeners(AuditingEntityListener.class)     // 저장·수정 시점에 끼어들어 값을 채워줄 담당자를 지정한다
public abstract class BaseEntity {

    /**
     * 처음 저장될 때 JPA가 자동으로 채운다.
     *
     * updatable = false 가 중요하다.
     *   이 컬럼을 UPDATE 문에서 아예 제외한다는 뜻이다.
     *   '만들어진 시각'은 나중에 바뀌면 안 되는 값이므로,
     *   실수로도 덮어쓰이지 않도록 JPA 차원에서 막아둔다.
     */
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 저장될 때와 수정될 때마다 JPA가 자동으로 갱신한다.
     *
     * 사람이 직접 넣는 방식이면 수정할 때 갱신을 빠뜨리기 쉽고,
     * 그러면 '마지막 수정 시각'을 믿을 수 없게 되어 장애 추적에 쓸 수 없다.
     */
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
