package com.foodmemory.app.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.foodmemory.app.dto.PlaceCandidate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 카카오 로컬 API 로 좌표 주변의 장소를 검색한다.
 *
 * 브라우저가 아니라 서버가 직접 호출한다. 그래서 도메인 등록과 무관하고,
 * API 키가 사용자에게 노출되지도 않는다. 브라우저에서 부르면 키가 그대로 드러난다.
 */
@Slf4j
@Component
public class KakaoLocalClient {

    private static final String BASE_URL = "https://dapi.kakao.com";

    /** 카카오가 정한 분류 코드. FD6 은 음식점이다. */
    private static final String CATEGORY_RESTAURANT = "FD6";

    /**
     * 좌표를 소수점 7자리로 맞춘다. 장소 테이블의 DECIMAL(10, 7) 과 같은 정밀도다.
     *
     * 카카오는 "127.02830563642301" 처럼 17자리까지 준다. 그대로 저장하면
     * MySQL 이 알아서 반올림하지만, 값이 잘린다는 사실이 코드에 드러나지 않는다.
     * 받는 자리에서 맞춰두면 저장되는 값이 눈에 보인다.
     * 7자리면 약 1cm 단위라 장소 위치로는 넘치도록 충분하다.
     */
    private static final int COORD_SCALE = 7;

    private final RestClient restClient;
    private final String apiKey;
    private final int radius;

    public KakaoLocalClient(@Value("${app.kakao.rest-api-key}") String apiKey,
                            @Value("${app.kakao.search-radius}") int radius) {
        this.apiKey = apiKey;
        this.radius = radius;
        this.restClient = RestClient.builder().baseUrl(BASE_URL).build();
    }

    /** 키가 설정돼 있는지. 없으면 검색 기능을 막고 안내한다. */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * 좌표 주변의 음식점을 가까운 순으로 가져온다.
     *
     * 한 좌표에 장소가 여러 개일 수 있다. 같은 건물에 다섯 곳이 있으면
     * GPS 로는 구분되지 않는다. 그래서 하나를 자동으로 고르지 않고
     * 후보를 그대로 돌려주어 사용자가 선택하게 한다.
     */
    public List<PlaceCandidate> searchNearby(BigDecimal latitude, BigDecimal longitude) {
        if (!isConfigured()) {
            log.warn("카카오 API 키가 설정되지 않아 검색을 건너뜁니다.");
            return List.of();
        }

        try {
            JsonNode body = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/local/search/category.json")
                            .queryParam("category_group_code", CATEGORY_RESTAURANT)
                            .queryParam("x", longitude)   // 카카오는 x 가 경도다. 순서를 헷갈리기 쉽다
                            .queryParam("y", latitude)    // y 가 위도
                            .queryParam("radius", radius)
                            .queryParam("sort", "distance")
                            .queryParam("size", 15)
                            .build())
                    // 인증 형식이 정해져 있다. "KakaoAK " 뒤에 키를 붙인다
                    .header("Authorization", "KakaoAK " + apiKey)
                    .retrieve()
                    .body(JsonNode.class);

            return parse(body);

        } catch (Exception e) {
            // 외부 서비스 호출은 언제든 실패할 수 있다.
            // 검색이 안 됐다고 게시물 조회 자체가 막히면 안 되므로 빈 목록을 돌려준다.
            log.error("카카오 장소 검색에 실패했습니다.", e);
            return List.of();
        }
    }

    /**
     * 이름으로 장소를 찾는다.
     *
     * 좌표 검색만으로는 부족해서 이 경로가 반드시 필요하다.
     *   - iOS Safari 는 사진을 업로드할 때 GPS 를 지운다. 애플의 정책이라 우회할 수 없다
     *   - 메신저를 거친 사진도 좌표가 지워진다
     *   - 위치 서비스를 꺼두고 찍었거나, 실내라 좌표가 부정확한 경우도 있다
     *
     * 즉 EXIF 는 '있으면 빠른 길' 이지 유일한 입구가 될 수 없다.
     * 좌표가 없어도 사용자가 직접 찾아 연결할 수 있어야 기능이 성립한다.
     *
     * @param latitude  사진 좌표. 있으면 가까운 순으로 정렬한다. 없으면 null
     * @param longitude 사진 좌표. 있으면 가까운 순으로 정렬한다. 없으면 null
     */
    public List<PlaceCandidate> searchByKeyword(String keyword,
                                             BigDecimal latitude, BigDecimal longitude) {
        if (!isConfigured()) {
            log.warn("카카오 API 키가 설정되지 않아 검색을 건너뜁니다.");
            return List.of();
        }
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }

        boolean hasCenter = latitude != null && longitude != null;

        try {
            JsonNode body = restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/v2/local/search/keyword.json")
                                .queryParam("query", keyword.trim())
                                .queryParam("size", 15);

                        /*
                         * 여기서는 분류를 제한하지 않는다.
                         *
                         * 처음에는 음식점(FD6)으로 좁혀두었는데, 그러면 해수욕장·공원·휴게소처럼
                         * 음식점으로 등록되지 않은 장소가 전부 걸러진다.
                         * 바닷가에서 먹은 것도, 캠핑장에서 먹은 것도 이 서비스의 기록이다.
                         *
                         * 사용자가 이름을 직접 입력했다는 것은 무엇을 찾는지 안다는 뜻이다.
                         * 그걸 우리가 걸러내면 "검색했는데 안 나오는" 상황이 된다.
                         *
                         * 좌표로 찾을 때는 반대로 FD6 을 유지한다.
                         * 그건 사용자가 무엇을 찾는지 말하지 않은 상태라, 주변의 주차장·편의점까지
                         * 다 보여주면 후보가 소음이 된다.
                         */

                        if (hasCenter) {
                            // 좌표를 함께 주면 그 지점에서 가까운 순으로 정렬된다.
                            // 사진 위치는 알지만 후보에 원하는 가게가 없을 때(간판 이름이 달라서 등)
                            // 이름으로 찾으면서도 그 동네 결과를 먼저 보여줄 수 있다.
                            uriBuilder.queryParam("x", longitude)
                                    .queryParam("y", latitude)
                                    .queryParam("sort", "distance");
                        }
                        return uriBuilder.build();
                    })
                    .header("Authorization", "KakaoAK " + apiKey)
                    .retrieve()
                    .body(JsonNode.class);

            return parse(body);

        } catch (Exception e) {
            log.error("카카오 키워드 검색에 실패했습니다: {}", keyword, e);
            return List.of();
        }
    }

    private List<PlaceCandidate> parse(JsonNode body) {
        List<PlaceCandidate> places = new ArrayList<>();
        if (body == null || !body.has("documents")) {
            return places;
        }

        for (JsonNode doc : body.get("documents")) {
            // 도로명 주소가 없는 곳도 있어 지번 주소로 대체한다
            String road = doc.path("road_address_name").asText("");
            String jibun = doc.path("address_name").asText("");
            String address = road.isBlank() ? jibun : road;

            places.add(new PlaceCandidate(
                    doc.path("id").asText(),
                    doc.path("place_name").asText(),
                    address,
                    doc.path("category_name").asText(""),
                    doc.path("distance").asInt(0),
                    toCoordinate(doc.path("y").asText("0")),
                    toCoordinate(doc.path("x").asText("0"))
            ));
        }
        return places;
    }

    /** 문자열로 온 좌표를 저장할 정밀도에 맞춰 BigDecimal 로 바꾼다. */
    private BigDecimal toCoordinate(String value) {
        return new BigDecimal(value).setScale(COORD_SCALE, RoundingMode.HALF_UP);
    }
}
