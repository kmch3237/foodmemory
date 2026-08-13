package com.foodmemory.app.auth;

/**
 * 소셜 인증을 마친 뒤의 결과. 두 갈래뿐이다.
 *
 *   이미 가입한 사람  → 바로 로그인시킨다
 *   처음 오는 사람    → 약관 동의를 받아야 하므로 가입 화면으로 보낸다
 *
 * sealed 로 선언한 이유:
 *   "이 둘 외의 경우는 없다" 를 컴파일러에게 알려주는 것이다.
 *   그러면 switch 에서 한 갈래를 빠뜨렸을 때 컴파일이 실패한다.
 *   나중에 "탈퇴한 회원" 같은 갈래가 늘어나면, 처리하지 않은 곳을 컴파일러가 전부 짚어준다.
 *
 * boolean 플래그와 nullable 필드 두 개로 표현하지 않은 이유:
 *   registered=true 인데 loginMember 가 null 인 상태가 문법적으로 만들어질 수 있다.
 *   있을 수 없는 조합을 애초에 표현할 수 없게 만드는 편이 안전하다.
 */
public sealed interface OAuthAuthentication {

    /** 이미 우리 회원인 경우. */
    record Registered(LoginMember loginMember) implements OAuthAuthentication {
    }

    /** 아직 회원이 아닌 경우. 이 시점에는 DB 에 아무것도 저장하지 않았다. */
    record NotRegistered(PendingSignUp pending) implements OAuthAuthentication {
    }
}
