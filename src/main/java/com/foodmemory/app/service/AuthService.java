package com.foodmemory.app.service;

import com.foodmemory.app.auth.LoginMember;
import com.foodmemory.app.auth.OAuthAuthentication;
import com.foodmemory.app.auth.PendingSignUp;
import com.foodmemory.app.dto.LinkedIdentity;
import com.foodmemory.app.entity.Provider;

import java.util.List;

public interface AuthService {

    /** 이 사이트에서 직접 가입한다. 가입에 성공하면 곧바로 로그인 상태로 쓸 정보를 돌려준다. */
    LoginMember signUpLocal(String email, String password, String nickname, boolean agreedToTerms);

    /** 이메일과 비밀번호로 로그인한다. */
    LoginMember loginLocal(String email, String password);

    /**
     * 소셜에서 받은 인가 코드로 사용자를 확인한다.
     *
     * 여기서 회원을 만들지는 않는다. 처음 오는 사람이면 약관 동의를 받아야 하는데,
     * 그건 화면을 한 번 더 거쳐야 하는 일이라 이 메서드 안에서 끝낼 수 없다.
     * 그래서 "이미 회원인가 / 아닌가" 만 판별해서 돌려준다.
     */
    OAuthAuthentication authenticateWithOAuth(Provider provider, String code);

    /**
     * 약관에 동의한 소셜 사용자를 실제 회원으로 만든다.
     *
     * pending 은 화면이 아니라 세션에서 꺼내온 값이어야 한다.
     * 이유는 PendingSignUp 주석에 적어두었다.
     */
    LoginMember completeSocialSignUp(PendingSignUp pending, String nickname, boolean agreedToTerms);

    /**
     * 이미 로그인한 회원에게 소셜 계정을 하나 더 연결한다.
     *
     * 이메일이 같다고 자동으로 합치지 않고 이 경로만 두는 이유:
     *   제공자가 그 이메일을 검증했는지 우리는 알 수 없다.
     *   남의 이메일로 소셜 계정을 만들어 로그인하면 그 사람 계정을 통째로 가져갈 수 있다.
     *   "이미 로그인한 본인이 직접 연결을 눌렀다" 는 사실이 있어야 안전하다.
     */
    void linkIdentity(Long memberId, PendingSignUp pending);

    /** 이 회원에게 연결된 로그인 수단 목록. 계정 화면에서 보여준다. */
    List<LinkedIdentity> findLinkedIdentities(Long memberId);
}
