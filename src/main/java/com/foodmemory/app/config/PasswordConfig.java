package com.foodmemory.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 해싱 도구를 스프링 빈으로 등록한다.
 *
 * new BCryptPasswordEncoder() 를 필요한 곳마다 직접 만들지 않는 이유:
 *   해싱 방식을 바꾸거나 강도를 조절할 때 흩어진 곳을 전부 찾아 고쳐야 한다.
 *   한 곳이라도 놓치면 어떤 회원은 옛 방식으로 저장되어 로그인이 안 된다.
 *
 * 반환 타입을 BCryptPasswordEncoder 가 아니라 PasswordEncoder 로 둔 것도 같은 이유다.
 * 쓰는 쪽은 "해싱하고 비교할 수 있는 무언가" 만 알면 되고,
 * 그게 BCrypt 인지 Argon2 인지는 이 파일만 알면 된다.
 */
@Configuration
public class PasswordConfig {

    /**
     * BCrypt 를 쓰는 이유:
     *   SHA-256 같은 일반 해시는 너무 빠르다. 공격자가 유출된 해시를 두고
     *   초당 수억 번씩 대입해볼 수 있어서, 짧은 비밀번호는 금방 뚫린다.
     *   BCrypt 는 일부러 느리게 만들어져 그 대입 속도를 떨어뜨린다.
     *
     * 솔트를 따로 관리하지 않아도 되는 이유:
     *   BCrypt 는 매번 임의의 솔트를 만들어 결과 문자열 안에 함께 넣는다.
     *   그래서 같은 비밀번호라도 저장된 해시가 사람마다 다르고,
     *   미리 계산해둔 표(레인보우 테이블)로 한 번에 뚫는 방법이 통하지 않는다.
     *   검증할 때는 저장된 값에서 솔트를 읽어 같은 방식으로 다시 계산한다.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
