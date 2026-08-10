package com.foodmemory.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 식당 — restaurant 테이블과 매핑된다.
 *
 * 다른 엔티티와 성격이 다르다. 원본은 지도 API에 있고 이 테이블은 사본이다.
 */
@Entity
@Table(name = "restaurant")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Restaurant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long restaurantId;

    /**
     * 지도 API가 매긴 장소 ID. 같은 식당인지 판별하는 기준이다.
     *
     * unique = true 는 "이 컬럼에 유니크 제약이 있다"는 사실을 코드에 남기는 것이다.
     * ddl-auto: validate 는 컬럼의 존재와 타입을 검사할 뿐 제약까지 검사하지는 않으므로
     * 이 속성이 없어도 실행에는 지장이 없다. 다만 엔티티만 봐도 구조를 알 수 있게 적어둔다.
     */
    @Column(nullable = false, length = 50, unique = true)
    private String placeId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 255)
    private String address;

    /**
     * DDL의 DECIMAL(10, 7) 에 대응한다.
     *
     * 자바 타입이 double 이 아니라 BigDecimal 인 이유:
     *   DECIMAL 을 쓴 이유가 부동소수점 오차를 피하기 위해서였는데,
     *   자바에서 double 로 받으면 그 오차가 자바 쪽에서 그대로 생긴다.
     *   DB에서 정확히 꺼내와도 계산하는 순간 어긋난다는 뜻이다.
     *   BigDecimal 은 소수를 정확하게 다루는 타입이라 DECIMAL 과 짝이 맞는다.
     *
     * 문자열이 아니므로 length 대신 precision 과 scale 을 쓴다.
     *   precision = 10  →  전체 자릿수
     *   scale     = 7   →  소수점 아래 자릿수
     * DDL의 DECIMAL(10, 7) 과 정확히 같은 의미다.
     */
    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;
}
