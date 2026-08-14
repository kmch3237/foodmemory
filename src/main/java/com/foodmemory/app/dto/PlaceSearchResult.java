package com.foodmemory.app.dto;

import java.util.List;

/**
 * 장소 후보를 찾은 결과.
 *
 * 목록만 돌려주면 화면이 "왜 이 목록인지" 를 알 수 없다.
 * 사진 좌표로 찾은 것인지, 사용자가 이름으로 찾은 것인지, 아니면 좌표가 없어
 * 아직 아무것도 못 찾은 것인지에 따라 화면이 달라져야 한다.
 *
 * @param places           찾은 후보들. 비어 있을 수 있다
 * @param keyword          사용자가 입력한 검색어. 좌표로 찾았으면 null
 * @param photoHasLocation 사진에 좌표가 있었는지. 없으면 화면이 검색을 안내한다
 */
public record PlaceSearchResult(
        List<PlaceCandidate> places,
        String keyword,
        boolean photoHasLocation
) {

    /** 사진 좌표로 주변을 찾은 결과. */
    public static PlaceSearchResult byLocation(List<PlaceCandidate> places) {
        return new PlaceSearchResult(places, null, true);
    }

    /** 사용자가 입력한 이름으로 찾은 결과. */
    public static PlaceSearchResult byKeyword(List<PlaceCandidate> places, String keyword,
                                              boolean photoHasLocation) {
        return new PlaceSearchResult(places, keyword, photoHasLocation);
    }

    /**
     * 사진에 좌표가 없어 아직 아무것도 찾지 못한 상태.
     *
     * 예전에는 이 경우에 400 을 띄우고 끝냈다. 사용자 입장에서는 '안 되는 기능' 이었다.
     * 이제는 빈 결과를 돌려주고 화면이 검색창을 보여준다.
     */
    public static PlaceSearchResult noLocation() {
        return new PlaceSearchResult(List.of(), null, false);
    }

    /** 검색을 한 번이라도 시도했는지. 화면에서 '결과 없음' 을 보여줄지 판단한다. */
    public boolean searched() {
        return photoHasLocation || (keyword != null && !keyword.isBlank());
    }
}
