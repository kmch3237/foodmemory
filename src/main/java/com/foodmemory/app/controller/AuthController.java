package com.foodmemory.app.controller;

import com.foodmemory.app.auth.Login;
import com.foodmemory.app.auth.LoginMember;
import com.foodmemory.app.auth.PendingSignUp;
import com.foodmemory.app.auth.SessionConst;
import com.foodmemory.app.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /* ── 자체 회원가입 ───────────────────────────────────────── */

    @GetMapping("/signup")
    public String signUpForm() {
        return "auth/signup";
    }

    /**
     * 가입 처리. 성공하면 곧바로 로그인 상태로 만들어 첫 화면으로 보낸다.
     *
     * 가입 직후 로그인 화면으로 다시 보내지 않는 이유:
     *   방금 비밀번호를 두 번 입력한 사람에게 또 입력하라고 하는 셈이다.
     *   서버는 이미 그가 누구인지 알고 있으므로 다시 물을 이유가 없다.
     */
    @PostMapping("/signup")
    public String signUp(@RequestParam String email,
                         @RequestParam String password,
                         @RequestParam String nickname,
                         @RequestParam(defaultValue = "false") boolean agreeTerms,
                         @RequestParam(defaultValue = "false") boolean agreePrivacy,
                         HttpServletRequest request,
                         Model model) {
        try {
            LoginMember loginMember = authService.signUpLocal(
                    email, password, nickname, agreeTerms && agreePrivacy);
            startSession(request, loginMember);
            return "redirect:/";

        } catch (IllegalArgumentException e) {
            // 입력값을 다시 채워 보내 사용자가 처음부터 다시 쓰지 않게 한다.
            // 비밀번호는 돌려주지 않는다. 화면 소스에 남을 이유가 없다.
            model.addAttribute("error", e.getMessage());
            model.addAttribute("email", email);
            model.addAttribute("nickname", nickname);
            return "auth/signup";
        }
    }

    /* ── 소셜 가입 마무리 ─────────────────────────────────────── */

    /**
     * 소셜 인증은 끝났지만 아직 회원이 아닌 사람에게 보여주는 화면.
     *
     * 이 화면에 오기 전에 이미 카카오·구글이 본인 확인을 마쳤다.
     * 여기서 받는 것은 우리 서비스의 약관 동의와, 사용자가 직접 정하는 닉네임뿐이다.
     */
    @GetMapping("/signup/social")
    public String socialSignUpForm(HttpServletRequest request, Model model) {
        PendingSignUp pending = findPending(request);
        if (pending == null) {
            // 세션에 인증 흔적이 없다. 주소를 직접 친 경우이거나 세션이 만료된 경우다.
            // 이 화면만 따로 열어서 아무나 가입시킬 수는 없으므로 처음부터 다시 하게 한다.
            return "redirect:/login";
        }

        model.addAttribute("provider", pending.provider());
        model.addAttribute("nickname", pending.suggestedNickname());
        model.addAttribute("email", pending.email());
        return "auth/social-signup";
    }

    @PostMapping("/signup/social")
    public String socialSignUp(@RequestParam String nickname,
                               @RequestParam(defaultValue = "false") boolean agreeTerms,
                               @RequestParam(defaultValue = "false") boolean agreePrivacy,
                               HttpServletRequest request,
                               Model model) {

        PendingSignUp pending = findPending(request);
        if (pending == null) {
            return "redirect:/login";
        }

        try {
            LoginMember loginMember = authService.completeSocialSignUp(
                    pending, nickname, agreeTerms && agreePrivacy);

            HttpSession session = request.getSession(false);
            String redirectUrl = session == null
                    ? null : (String) session.getAttribute(SessionConst.REDIRECT_URL);

            startSession(request, loginMember);
            return "redirect:" + safeRedirect(redirectUrl);

        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("provider", pending.provider());
            model.addAttribute("nickname", nickname);
            model.addAttribute("email", pending.email());
            return "auth/social-signup";
        }
    }

    /* ── 계정 화면 ───────────────────────────────────────────── */

    /**
     * 연결된 로그인 수단을 보여주고, 새로 연결할 수 있게 한다.
     *
     * 이 화면에서 소셜 버튼을 누르면 로그인이 아니라 '연결' 로 동작한다.
     * 이미 로그인한 상태에서 콜백이 돌아오기 때문이고, 그 분기는 OAuthController 에 있다.
     */
    @GetMapping("/account")
    public String account(@Login LoginMember loginMember, Model model) {
        model.addAttribute("loginMember", loginMember);
        model.addAttribute("identities", authService.findLinkedIdentities(loginMember.memberId()));
        return "auth/account";
    }

    /** 세션에서 가입 대기 정보를 꺼낸다. 없으면 null. */
    private PendingSignUp findPending(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        return (PendingSignUp) session.getAttribute(SessionConst.PENDING_SIGN_UP);
    }

    /* ── 로그인 / 로그아웃 ───────────────────────────────────── */

    /**
     * 로그인 화면.
     *
     * redirectUrl 은 인터셉터가 붙여준다. 로그인하지 않은 채로 가려던 주소가 담겨 있다.
     * 화면의 숨은 입력칸에 넣어두었다가 로그인에 성공하면 그리로 돌려보낸다.
     */
    @GetMapping("/login")
    public String loginForm(@RequestParam(required = false) String redirectUrl, Model model) {
        model.addAttribute("redirectUrl", redirectUrl);
        return "auth/login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String email,
                        @RequestParam String password,
                        @RequestParam(required = false) String redirectUrl,
                        HttpServletRequest request,
                        Model model) {
        try {
            LoginMember loginMember = authService.loginLocal(email, password);
            startSession(request, loginMember);
            return "redirect:" + safeRedirect(redirectUrl);

        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("email", email);
            model.addAttribute("redirectUrl", redirectUrl);
            return "auth/login";
        }
    }

    /**
     * 로그아웃.
     *
     * GET 이 아니라 POST 인 이유:
     *   GET 이면 <img src="/logout"> 한 줄이 박힌 다른 사이트를 보기만 해도
     *   로그아웃된다. 서버의 상태를 바꾸는 동작은 GET 으로 두지 않는다.
     */
    @PostMapping("/logout")
    public String logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            // removeAttribute 가 아니라 invalidate 를 쓴다.
            // 세션에 남은 다른 값까지 함께 정리해야 흔적이 남지 않는다.
            session.invalidate();
        }
        return "redirect:/";
    }

    /* ── 공통 ────────────────────────────────────────────────── */

    /**
     * 로그인 상태를 시작한다.
     *
     * 기존 세션을 버리고 새로 만드는 이유 (세션 고정 공격 방지):
     *   공격자가 미리 만든 세션 ID 를 피해자 브라우저에 심어둔 뒤 로그인을 유도하면,
     *   로그인 후에도 세션 ID 가 그대로라 공격자가 같은 ID 로 그 계정에 들어갈 수 있다.
     *   로그인하는 순간 세션을 새로 발급하면 공격자가 아는 ID 는 쓸모없어진다.
     */
    private void startSession(HttpServletRequest request, LoginMember loginMember) {
        HttpSession previous = request.getSession(false);
        if (previous != null) {
            previous.invalidate();
        }

        HttpSession session = request.getSession(true);
        session.setAttribute(SessionConst.LOGIN_MEMBER, loginMember);
    }

    /**
     * 돌아갈 주소를 검사한다.
     *
     * 넘어온 값을 그대로 redirect 에 넣으면 안 된다.
     *   /login?redirectUrl=https://악성사이트
     * 같은 주소를 메신저로 뿌리면, 사용자는 우리 도메인의 로그인 화면을 보고 로그인했는데
     * 곧바로 남의 사이트로 옮겨진다. 그 사이트가 똑같이 생긴 로그인 화면을 띄우면
     * 방금 우리 사이트에서 로그인한 사람은 의심 없이 비밀번호를 다시 입력한다.
     *
     * 그래서 "/" 로 시작하는 우리 사이트 안의 경로만 허용한다.
     * "//" 로 시작하는 값은 브라우저가 다른 도메인으로 해석하므로 함께 막는다.
     */
    private String safeRedirect(String redirectUrl) {
        if (redirectUrl == null || redirectUrl.isBlank()) {
            return "/";
        }
        if (!redirectUrl.startsWith("/") || redirectUrl.startsWith("//")) {
            return "/";
        }
        return redirectUrl;
    }
}
