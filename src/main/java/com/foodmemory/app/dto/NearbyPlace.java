package com.foodmemory.app.dto;

import java.math.BigDecimal;

/**
 * 지도 API 가 알려준 주변 장소 하나.
 *
 * 아직 우리 DB 에 저장된 식당이 아니다. 사용자가 고르면 그때 식당 테이블에 들어간다.
 * placeId 는 지도 API 가 매긴 번호이고, 같은 식당인지 판별하는 기준이 된다.
 */
public record NearbyPlace(
        String placeId,
        String name,
        String address,
        String categoryName,   // 예) 음식점 > 한식 > 국밥
        int distanceMeters,
        BigDecimal latitude,
        BigDecimal longitude
) {
}
