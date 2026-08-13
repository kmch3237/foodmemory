package com.foodmemory.app.controller;

import com.foodmemory.app.auth.LoginMember;
import com.foodmemory.app.auth.OAuthAuthentication;
import com.foodmemory.app.auth.SessionConst;
import com.foodmemory.app.auth.oauth.OAuthClients;
import com.foodmemory.app.entity.Provider;
import com.foodmemory.app.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 소셜 로그인. 카카오와 구글이 같은 코드를 쓴다.
 *
 * 흐름은 두 번의 요청으로 나뉜다.
 *
 *   1. GET /oauth/kakao           우리 → 사용자를 카카오로 보냄
 *   2. GET /oauth/kakao/callback  카카오 → 인가 코드를 들고 사용자를 우리에게 돌려보냄
 *
 * 중간에 사용자의 브라우저가 카카오를 다녀오므로, 두 요청은 서로 다른 요청이다.
 * 그 사이에 이어져 있어야 하는 값(state, 돌아갈 주소)은 세션에 맡긴다.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class OAuthController {

    private final AuthService authService;
    private final OAuthClients oAuthClients;

    /**
     * 예측할 수 없는 값을 만든다.
     *
     * Random 이 아니라 SecureRandom 인 이유:
     *   Random 은 규칙적으로 만들어져 이전 값 몇 개를 보면 다음 값을 계산할 수 있다.
     *   state 는 맞히지 못해야 의미가 있으므로 암호학적으로 안전한 난수를 쓴다.
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 1단계 — 제공자의 로그인 화면으로 보낸다. */
    @GetMapping("/oauth/{provider}")
    public String start(@PathVariable String provider,
                        @RequestParam(required = false) String redirectUrl,
                        HttpServletRequest request,
                        RedirectAttributes redirectAttributes) {

        Provider target = Provider.from(provider);
        if (target == Provider.LOCAL) {
            throw new IllegalArgumentException("소셜 로그인이 아닙니다.");
        }

        try {
            String state = generateState();

            // 세션에 적어둔다. 돌아왔을 때 같은 값인지 확인해야 하기 때문이다.
            HttpSession session = request.getSession(true);
            session.setAttribute(SessionConst.OAUTH_STATE, state);
            session.setAttribute(SessionConst.REDIRECT_URL, redirectUrl);

            return "redirect:" + oAuthClients.get(target).authorizeUrl(state);

        } catch (IllegalStateException e) {
            // 아직 키가 설정되지 않은 경우. 로그인 화면으로 돌려보내 이유를 알려준다.
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/login";
        }
    }

    /**
     * 2단계 — 제공자가 인가 코드를 들려 보낸 사용자를 받는다.
     *
     * @param code  인가 코드. 동의를 거부하면 오지 않는다
     * @param state 우리가 1단계에서 보냈던 값. 그대로 돌아와야 한다
     * @param error 사용자가 동의를 거부했을 때 제공자가 담아 보내는 값
     */
    @GetMapping("/oauth/{provider}/callback")
    public String callback(@PathVariable String provider,
                           @RequestParam(required = false) String code,
                           @RequestParam(required = false) String state,
                           @RequestParam(required = false) String error,
                           HttpServletRequest request,
                           RedirectAttributes redirectAttributes) {

        HttpSession session = request.getSession(false);
        String savedState = session == null ? null : (String) session.getAttribute(SessionConst.OAUTH_STATE);
        String redirectUrl = session == null ? null : (String) session.getAttribute(SessionConst.REDIRECT_URL);

        // 한 번 쓴 state 는 바로 지운다. 남겨두면 같은 값으로 다시 시도할 수 있다.
        if (session != null) {
            session.removeAttribute(SessionConst.OAUTH_STATE);
            session.removeAttribute(SessionConst.REDIRECT_URL);
        }

        if (error != null) {
            log.info("{} 로그인 취소: {}", provider, error);
            redirectAttributes.addFlashAttribute("error", "로그인이 취소되었습니다.");
            return "redirect:/login";
        }

        /*
         * state 검사 (CSRF 방지).
         *
         * 이게 없으면 공격자가 자기 계정의 인가 코드로 만든 콜백 주소를 피해자에게 눌리게 해서,
         * 피해자 브라우저를 공격자 계정으로 로그인시킬 수 있다.
         * 그 상태에서 피해자가 올린 사진은 공격자 계정에 쌓인다.
         *
         * equals 를 저장된 값 쪽에서 호출하지 않는 이유: 저장된 값이 null 일 수 있다.
         */
        if (savedState == null || !savedState.equals(state)) {
            log.warn("state 불일치. 위조된 콜백일 수 있습니다. provider={}", provider);
            redirectAttributes.addFlashAttribute("error", "로그인 요청이 올바르지 않습니다. 다시 시도해주세요.");
            return "redirect:/login";
        }

        if (code == null || code.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "인가 코드를 받지 못했습니다.");
            return "redirect:/login";
        }

        // 이미 로그인한 상태에서 소셜 버튼을 눌렀다면 '계정 연결' 로 본다.
        // 로그인하러 온 것이 아니라, 쓰던 계정에 로그인 수단을 하나 더 붙이려는 것이다.
        LoginMember current = (LoginMember) session.getAttribute(SessionConst.LOGIN_MEMBER);

        try {
            OAuthAuthentication result =
                    authService.authenticateWithOAuth(Provider.from(provider), code);

            if (current != null) {
                return linkToCurrentMember(current, result, redirectAttributes);
            }

            // 인증을 마쳤으므로 세션을 새로 만든다. 이유는 AuthController 의 startSession 과 같다.
            session.invalidate();
            HttpSession fresh = request.getSession(true);

            /*
             * sealed 인터페이스라 갈래가 둘뿐임이 보장된다.
             * 나중에 갈래가 늘어나면 이 switch 가 컴파일되지 않아 고칠 곳을 알려준다.
             */
            return switch (result) {

                case OAuthAuthentication.Registered r -> {
                    fresh.setAttribute(SessionConst.LOGIN_MEMBER, r.loginMember());
                    yield "redirect:" + safeRedirect(redirectUrl);
                }

                case OAuthAuthentication.NotRegistered n -> {
                    // 아직 회원이 아니다. DB 에는 아무것도 저장하지 않은 상태로
                    // 약관 동의 화면으로 보낸다. 동의해야 비로소 회원이 된다.
                    fresh.setAttribute(SessionConst.PENDING_SIGN_UP, n.pending());
                    fresh.setAttribute(SessionConst.REDIRECT_URL, redirectUrl);
                    yield "redirect:/signup/social";
                }
            };

        } catch (IllegalStateException | IllegalArgumentException e) {
            log.error("{} 로그인 처리 실패", provider, e);
            redirectAttributes.addFlashAttribute("error", "로그인에 실패했습니다. 잠시 후 다시 시도해주세요.");
            return "redirect:/login";
        }
    }

    /**
     * 로그인한 회원에게 방금 인증한 소셜 계정을 붙인다.
     *
     * 이미 다른 회원으로 가입된 계정이면 붙이지 않는다.
     * 옮겨오면 그쪽 사람이 로그인 수단을 잃기 때문이다. 자세한 이유는 AuthService 에 적어두었다.
     */
    private String linkToCurrentMember(LoginMember current,
                                       OAuthAuthentication result,
                                       RedirectAttributes redirectAttributes) {
        if (result instanceof OAuthAuthentication.Registered r) {
            if (r.loginMember().memberId().equals(current.memberId())) {
                redirectAttributes.addFlashAttribute("message", "이미 연결된 계정입니다.");
            } else {
                redirectAttributes.addFlashAttribute("error",
                        "이 계정은 이미 다른 회원에 연결되어 있습니다.");
            }
            return "redirect:/account";
        }

        OAuthAuthentication.NotRegistered n = (OAuthAuthentication.NotRegistered) result;
        try {
            authService.linkIdentity(current.memberId(), n.pending());
            redirectAttributes.addFlashAttribute("message", "계정을 연결했습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/account";
    }

    private String generateState() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        // 주소에 담아 보낼 값이라 URL 에 써도 안전한 형식으로 바꾼다.
        // withoutPadding 은 끝에 붙는 '=' 를 없앤다. 주소에서 인코딩이 필요해 번거롭다.
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 우리 사이트 안의 경로만 허용한다. 이유는 AuthController 에 적어두었다. */
    private String safeRedirect(String redirectUrl) {
        if (redirectUrl == null || redirectUrl.isBlank()
                || !redirectUrl.startsWith("/") || redirectUrl.startsWith("//")) {
            return "/";
        }
        return redirectUrl;
    }
}
