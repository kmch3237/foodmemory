package com.foodmemory.app.common;

/**
 * 로그인은 했지만 그 일을 할 자격이 없을 때.
 *
 * NotFoundException 과 구분하는 이유:
 *   없는 것과 권한이 없는 것은 다른 상황이고, 사용자에게 할 말도 다르다.
 *
 * 로그인하지 않은 경우와도 다르다.
 *   로그인 안 함 → 로그인하면 해결된다. 인터셉터가 로그인 화면으로 보낸다
 *   권한 없음    → 로그인해도 해결되지 않는다. 남의 글이기 때문이다
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
