package com.foodmemory.app.repository;

import com.foodmemory.app.entity.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    /**
     * 지도 API 의 장소 ID 로 이미 저장된 식당을 찾는다.
     *
     * 같은 식당에 여러 사람이 다녀오면 저장 요청도 여러 번 온다.
     * 그때마다 새로 넣으면 같은 가게가 여러 줄로 쌓여 식당별 모아보기가 깨진다.
     * place_id 에 UNIQUE 를 걸어두었으므로 결과는 0건 또는 1건이다.
     */
    Optional<Restaurant> findByPlaceId(String placeId);
}
