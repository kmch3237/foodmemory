package com.foodmemory.app.auth;

import com.foodmemory.app.entity.Provider;

import java.io.Serializable;

/**
 * 소셜 인증은 끝났지만 아직 우리 회원이 되지 않은 사람.
 *
 * 카카오·구글이 "이 사람 본인 맞다" 를 확인해준 결과를 잠시 들고 있는 것이다.
 * 약관에 동의하는 순간 이 정보로 Member 를 만든다.
 *
 * ── 반드시 세션에 담아야 하는 이유 ──
 *
 * 이 값을 가입 화면의 숨은 입력칸에 실어 보내면 안 된다.
 *
 *     <input type="hidden" name="providerUserId" value="5037008413">
 *
 * 개발자 도구로 이 값을 남의 카카오 회원 번호로 바꿔서 보내면, 카카오 인증을
 * 전혀 거치지 않고 그 사람 명의의 계정을 만들 수 있다.
 * 인증으로 확인된 값은 브라우저를 거치게 두지 않는다. 서버가 들고 있는다.
 *
 * 화면에서 받아도 되는 것은 사용자가 스스로 정하는 값(닉네임, 동의 여부)뿐이다.
 */
public record PendingSignUp(
        Provider provider,
        String providerUserId,

        /** 제공자가 알려준 이름. 가입 화면의 닉네임 칸에 미리 채워준다. 없을 수 있다 */
        String suggestedNickname,

        /** 제공자가 알려준 이메일. 카카오처럼 주지 않는 경우가 있어 없을 수 있다 */
        String email
) implements Serializable {
}
