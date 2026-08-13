package com.foodmemory.app.dto;

import com.foodmemory.app.entity.Provider;

import java.time.LocalDateTime;

/**
 * 계정 화면에 보여줄 로그인 수단 하나.
 *
 * providerUserId 와 passwordHash 는 담지 않는다.
 * 화면에 보여줄 이유가 없고, 내보내면 그만큼 새어나갈 통로가 늘어난다.
 */
public record LinkedIdentity(
        Provider provider,
        String description,        // 화면에 표시할 설명. 예) 이메일 로그인 (kmch@example.com)
        LocalDateTime linkedAt
) {
}
