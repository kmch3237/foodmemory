package com.foodmemory.app.entity;

/**
 * 회원이 어떤 경로로 들어왔는지.
 *
 * 코드 테이블(provider 테이블)로 빼지 않고 enum 으로 두는 이유:
 *   제공자를 하나 늘리려면 연동 코드를 새로 써야 한다. 데이터만 추가해서 되는 일이 아니다.
 *   그렇다면 코드에 두어야 컴파일러가 빠진 곳을 잡아준다.
 *
 * 문자열 대신 enum 인 이유:
 *   "kakao" 를 "kakoo" 로 잘못 적어도 String 이면 컴파일이 통과하고,
 *   그 회원은 영영 로그인되지 않는 상태로 저장된다. enum 은 그 자리에서 잡힌다.
 */
public enum Provider {

    KAKAO,
    GOOGLE,

    /** 소셜을 거치지 않고 이 사이트에서 직접 가입한 회원. 이 경우에만 비밀번호가 있다. */
    LOCAL;

    /**
     * 주소에 담겨 온 문자열을 enum 으로 바꾼다. 예) "kakao" → KAKAO
     *
     * valueOf 를 그대로 쓰지 않는 이유:
     *   대소문자가 다르면 실패하고, 없는 값이면 IllegalArgumentException 이 그대로 올라간다.
     *   메시지에 "No enum constant..." 만 남아서 무엇이 잘못됐는지 알기 어렵다.
     */
    public static Provider from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("로그인 방식이 지정되지 않았습니다.");
        }
        try {
            return Provider.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("지원하지 않는 로그인 방식입니다: " + value);
        }
    }

    /** 소셜 로그인인지. LOCAL 만 아니면 소셜이다. */
    public boolean isSocial() {
        return this != LOCAL;
    }
}
