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
 * 장소 — place 테이블과 매핑된다.
 *
 * 처음에는 Restaurant(식당)이었는데 Place(장소)로 바꿨다.
 * 해수욕장, 캠핑장, 휴게소처럼 음식점으로 등록되지 않은 곳에서 먹은 기록도 있기 때문이다.
 * 이름이 담는 범위보다 실제로 저장하는 범위가 넓어지면, 나중에 코드를 읽는 사람이
 * 구조를 잘못 이해한다. provider_id 를 provider_user_id 로 바꿨던 것과 같은 이유다.
 *
 * 다른 엔티티와 성격이 다르다. 원본은 지도 API에 있고 이 테이블은 사본이다.
 */
@Entity
@Table(name = "place")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Place extends BaseEntity {

    /** 우리가 매긴 인조키. 게시물이 이 값을 참조한다. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long placeId;

    /**
     * 카카오가 매긴 장소 ID. 같은 장소인지 판별하는 기준이다.
     *
     * 컬럼 이름에 kakao 를 붙인 이유:
     *   우리 PK 도 place_id 라서, 그냥 place_id 라고 두면 어느 쪽인지 구분되지 않는다.
     *   "우리 번호" 와 "카카오 번호" 는 전혀 다른 값인데 이름이 같으면 반드시 헷갈린다.
     *
     * unique = true 는 "이 컬럼에 유니크 제약이 있다"는 사실을 코드에 남기는 것이다.
     * ddl-auto: validate 는 컬럼의 존재와 타입을 검사할 뿐 제약까지 검사하지는 않으므로
     * 이 속성이 없어도 실행에는 지장이 없다. 다만 엔티티만 봐도 구조를 알 수 있게 적어둔다.
     */
    @Column(nullable = false, length = 50, unique = true)
    private String kakaoPlaceId;

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

    /**
     * 지도 API 에서 받은 정보로 장소를 만든다.
     *
     * 우리가 만들어내는 데이터가 아니라 외부에서 받아 보관하는 사본이다.
     * 그래서 값을 검증하거나 가공하지 않고 받은 그대로 저장한다.
     */
    public static Place from(String kakaoPlaceId, String name, String address,
                             BigDecimal latitude, BigDecimal longitude) {
        Place place = new Place();
        place.kakaoPlaceId = kakaoPlaceId;
        place.name = name;
        place.address = address;
        place.latitude = latitude;
        place.longitude = longitude;
        return place;
    }
}
