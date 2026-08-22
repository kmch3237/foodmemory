package com.foodmemory.app.common;

/**
 * 짧은 시간에 너무 많이 시도한 경우.
 *
 * IllegalArgumentException 을 쓰지 않는 이유:
 *   그것은 "입력이 잘못됐다" 는 뜻이라 400 오류 화면으로 간다.
 *   여기서 알려야 할 것은 "입력이 틀렸다" 가 아니라 "잠시 뒤에 다시 하라" 다.
 *   사용자가 할 일이 다르므로 예외도 나뉘어야 한다.
 *
 * IllegalStateException 도 쓸 수 없다.
 *   GlobalExceptionHandler 에서 그것은 500(서버 잘못)으로 처리된다.
 *   너무 많이 시도한 것은 서버 잘못이 아니다.
 *
 * 오류 화면으로 보내지 않고 컨트롤러가 직접 잡아 안내 문구만 띄운다.
 * 코드를 잘못 눌렀을 뿐인데 빨간 오류 화면이 뜨면 사고가 난 것처럼 보인다.
 */
public class TooManyAttemptsException extends RuntimeException {

    public TooManyAttemptsException(String message) {
        super(message);
    }
}
