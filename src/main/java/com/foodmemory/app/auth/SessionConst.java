package com.foodmemory.app.auth;

/**
 * 세션에 값을 넣고 뺄 때 쓰는 이름.
 *
 * 세션은 문자열을 열쇠로 쓰는 보관함이다.
 *   session.setAttribute("loginMember", ...)
 *   session.getAttribute("loginMemebr")      ← 오타. 컴파일은 통과하고 항상 null 이 나온다
 *
 * 이런 실수는 로그인이 안 되는 증상으로만 드러나서 원인을 찾기 어렵다.
 * 이름을 한 곳에 모아 상수로 두면 오타가 컴파일 단계에서 잡힌다.
 */
public final class SessionConst {

    /** 로그인한 회원 정보를 담아두는 자리. */
    public static final String LOGIN_MEMBER = "loginMember";

    /**
     * 로그인하지 않은 채로 접근하려던 주소.
     *
     * 로그인 후에 원래 가려던 곳으로 돌려보내기 위해 잠시 적어둔다.
     * 이게 없으면 사용자는 로그인할 때마다 첫 화면으로 튕겨서 다시 찾아가야 한다.
     */
    public static final String REDIRECT_URL = "redirectUrl";

    /**
     * 소셜 로그인을 시작할 때 만든 임의의 값.
     *
     * 사용자가 카카오·구글을 다녀오는 사이 우리 서버는 아무것도 기억하지 못한다.
     * 돌아온 요청이 정말 우리가 보낸 그 요청인지 확인하려면 표식을 남겨두어야 한다.
     */
    public static final String OAUTH_STATE = "oauthState";

    /**
     * 소셜 인증은 끝났지만 아직 약관에 동의하지 않은 사람의 정보.
     *
     * 가입 화면을 그리는 요청과 가입을 처리하는 요청은 서로 다른 요청이다.
     * 그 사이에 이 값을 들고 있어야 하는데, 화면에 실어 보내면 조작할 수 있어
     * 세션에 둔다. 자세한 이유는 PendingSignUp 주석에 적어두었다.
     */
    public static final String PENDING_SIGN_UP = "pendingSignUp";

    // 인스턴스를 만들 이유가 없는 클래스다. 생성자를 막아 의도를 분명히 한다.
    private SessionConst() {
    }
}
