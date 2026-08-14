package com.foodmemory.app.dto;

import java.math.BigDecimal;

/**
 * 지도 API 가 알려준 장소 하나. 아직 후보일 뿐이다.
 *
 * 우리 DB 의 Place 와 다르다. 사용자가 고르면 그때 place 테이블에 들어간다.
 * 그래서 식별자 이름도 kakaoPlaceId 다. 우리 placeId 는 저장한 뒤에야 생긴다.
 *
 * 음식점만 담는 것이 아니다. 이름으로 검색하면 해수욕장·관광지도 들어온다.
 */
public record PlaceCandidate(
        String kakaoPlaceId,
        String name,
        String address,
        String categoryName,   // 예) 음식점 > 한식 > 국밥, 여행 > 관광,명소 > 해수욕장
        int distanceMeters,    // 좌표 검색일 때만 값이 있다. 이름 검색이면 0
        BigDecimal latitude,
        BigDecimal longitude
) {
}
