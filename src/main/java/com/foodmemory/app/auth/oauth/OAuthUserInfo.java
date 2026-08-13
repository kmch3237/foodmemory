package com.foodmemory.app.auth.oauth;

/**
 * 소셜에서 받아온 사용자 정보를 우리 형태로 통일한 것.
 *
 * 카카오와 구글은 응답 모양이 전혀 다르다.
 *   카카오 : { "id": 12345, "kakao_account": { "profile": { "nickname": "철수" } } }
 *   구글   : { "sub": "1087...", "name": "철수", "email": "..." }
 *
 * 이 차이를 그대로 위층으로 올려보내면, 회원을 만드는 코드가 제공자마다 갈라진다.
 * 제공자를 하나 추가할 때마다 if 문이 하나씩 늘어난다.
 * 각 클라이언트가 자기 응답을 이 형태로 바꿔서 넘기면, 위층은 제공자를 몰라도 된다.
 *
 * @param providerUserId 그 제공자가 매긴 고유 번호. 닉네임·이메일과 달리 바뀌지 않는다
 * @param nickname       표시용 이름. 동의하지 않았으면 없을 수 있다
 * @param email          제공자가 주지 않을 수 있어 null 이 가능하다
 */
public record OAuthUserInfo(
        String providerUserId,
        String nickname,
        String email
) {
}
