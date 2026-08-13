package com.foodmemory.app.common;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 사진에서 꺼낸 정보.
 *
 * 셋 다 없을 수 있다.
 *   - 카카오톡 등을 거친 사진은 EXIF 가 제거된다
 *   - 폰에서 위치 저장을 꺼두면 좌표가 없다
 *   - 캡처 이미지처럼 촬영 시각이 없는 파일도 있다
 * 그래서 값이 없는 경우를 반드시 처리해야 한다.
 */
public record PhotoMetadata(
        LocalDateTime takenAt,
        BigDecimal latitude,
        BigDecimal longitude
) {

    public static PhotoMetadata empty() {
        return new PhotoMetadata(null, null, null);
    }

    public boolean hasLocation() {
        return latitude != null && longitude != null;
    }

    public boolean hasTakenAt() {
        return takenAt != null;
    }
}
