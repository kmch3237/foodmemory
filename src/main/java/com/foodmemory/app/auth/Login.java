package com.foodmemory.app.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 파라미터에 붙이면 지금 로그인한 회원이 들어온다.
 *
 *     public String upload(@Login LoginMember loginMember) { ... }
 *
 * 이게 없으면 컨트롤러마다 이 세 줄이 반복된다.
 *
 *     HttpSession session = request.getSession(false);
 *     if (session == null) { ... }
 *     LoginMember loginMember = (LoginMember) session.getAttribute("loginMember");
 *
 * 반복 자체보다 나쁜 것은, 한 곳에서 형변환이나 null 검사를 빠뜨려도
 * 그 컨트롤러에서만 조용히 터진다는 점이다. 꺼내는 방법을 한 곳으로 모은다.
 *
 * @Target(PARAMETER) : 메서드 파라미터에만 붙일 수 있다
 * @Retention(RUNTIME): 실행 중에도 이 표시가 남아 있어야 한다.
 *                      기본값은 클래스 파일까지만 남고 실행 시에는 사라져서,
 *                      실행 중에 읽어야 하는 리졸버가 이 표시를 볼 수 없다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Login {
}
