package com.foodmemory.app.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * @Login 이 붙은 파라미터에 세션 속 로그인 정보를 넣어준다.
 *
 * Spring 은 컨트롤러 메서드를 부르기 전에 파라미터를 하나씩 살펴보며
 * "이 자리를 채울 줄 아는 리졸버가 있는가" 를 묻는다.
 * supportsParameter 가 true 를 돌려주면 resolveArgument 가 값을 만들어 넣는다.
 * @RequestParam 이나 @PathVariable 도 같은 방식으로 동작한다.
 */
@Component
public class LoginArgumentResolver implements HandlerMethodArgumentResolver {

    /**
     * 두 조건을 모두 본다.
     *   - @Login 이 붙어 있는가
     *   - 타입이 LoginMember 인가
     *
     * 타입까지 확인하는 이유: @Login String nickname 처럼 잘못 쓰면
     * 형변환 오류가 실행 중에 터진다. 여기서 걸러내면 아예 후보에 오르지 않는다.
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(Login.class)
                && LoginMember.class.isAssignableFrom(parameter.getParameterType());
    }

    /**
     * 로그인하지 않았으면 null 을 넣는다. 예외를 던지지 않는 이유:
     *   갤러리처럼 로그인해도 되고 안 해도 되는 화면이 있다.
     *   그런 곳은 null 을 받아 "로그인" 버튼을 보여주면 된다.
     *   반드시 로그인해야 하는 경로는 인터셉터가 미리 막으므로,
     *   거기까지 왔다면 null 이 아님이 보장된다.
     */
    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {

        HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest();

        // getSession(false) 는 "없으면 만들지 말고 null 을 달라" 는 뜻이다.
        // 기본값인 true 로 두면 로그인하지 않은 방문자마다 빈 세션이 만들어져 메모리에 쌓인다.
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }

        return session.getAttribute(SessionConst.LOGIN_MEMBER);
    }
}
