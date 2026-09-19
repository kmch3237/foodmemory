package com.foodmemory.app.auth;

import com.foodmemory.app.repository.MemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 로그인이 필요한 요청을 컨트롤러에 닿기 전에 막는다.
 *
 * 컨트롤러마다 로그인 여부를 검사하지 않는 이유:
 *   검사 코드가 컨트롤러 수만큼 늘어나고, 새 화면을 추가하면서 한 곳만 빠뜨리면
 *   그 화면만 조용히 뚫린다. 빠뜨렸다는 사실이 눈에 띄지도 않는다.
 *   출입구를 한 곳으로 모으고, 어디를 지킬지는 WebConfig 에 목록으로 적어둔다.
 *
 * preHandle 이 false 를 돌려주면 요청은 여기서 끝난다. 컨트롤러는 호출되지 않는다.
 */
@Slf4j
@RequiredArgsConstructor
public class LoginCheckInterceptor implements HandlerInterceptor {

    private final MemberRepository memberRepository;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        HttpSession session = request.getSession(false);

        if (session != null
                && session.getAttribute(SessionConst.LOGIN_MEMBER) instanceof LoginMember loginMember) {

            /*
             * 세션이 있어도 회원이 아직 있는지 한 번 더 본다.
             *
             * 세션에는 로그인하던 순간의 회원 정보가 복사돼 30일 동안 남는다.
             * 폰에서 탈퇴해도 PC 브라우저의 세션은 그대로라, 그 상태로 사진을 올리면
             * 없는 회원을 가리키는 기록을 만들려다 500 오류가 난다.
             *
             * 탈퇴할 때 그 회원의 세션을 전부 찾아 지우는 방법도 있다.
             * 다만 Redis 저장 방식을 바꾸고 서버의 Redis 설정까지 손대야 하고,
             * 이미 발급된 세션에는 통하지 않는다.
             * 기본키 조회 한 번은 이 규모에서 부담이 없고, 어떤 세션이든 똑같이 걸러낸다.
             */
            if (memberRepository.existsById(loginMember.memberId())) {
                return true;   // 통과
            }

            log.info("탈퇴한 회원의 세션을 끊습니다: memberId={}", loginMember.memberId());
            session.invalidate();
        }

        String requestUri = request.getRequestURI();
        log.debug("로그인하지 않은 접근: {}", requestUri);

        /*
         * 원래 가려던 주소를 들고 로그인 화면으로 보낸다.
         * 로그인을 마치면 그 주소로 되돌려보내, 사용자가 처음부터 다시 찾아가지 않게 한다.
         *
         * 주소를 그대로 붙이지 않고 인코딩하는 이유:
         *   경로에 한글이나 ? & 같은 문자가 들어가면 물음표 뒤가 잘려나가
         *   엉뚱한 곳으로 돌아가게 된다.
         */
        String encoded = URLEncoder.encode(requestUri, StandardCharsets.UTF_8);
        response.sendRedirect("/login?redirectUrl=" + encoded);

        return false;   // 여기서 중단
    }
}
