package com.foodmemory.app.common;

/**
 * 요청한 자원이 없을 때 던진다.
 *
 * IllegalArgumentException 을 쓰지 않고 따로 만든 이유:
 *   "값이 잘못됨"과 "없는 것을 찾음"은 사용자에게 보여줄 결과가 다르다.
 *   전자는 잘못된 입력이고 후자는 404 다. 구분해두면 응답 코드를 다르게 줄 수 있다.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
