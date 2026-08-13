package com.foodmemory.app.auth.oauth;

import com.foodmemory.app.entity.Provider;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 제공자에 맞는 클라이언트를 찾아준다.
 *
 * Spring 은 같은 인터페이스를 구현한 빈들을 List 로 한꺼번에 주입해준다.
 * 그것을 제공자별로 꺼내 쓸 수 있게 Map 으로 정리해둔다.
 *
 * if (provider == KAKAO) ... else if (provider == GOOGLE) ... 로 쓰지 않는 이유:
 *   제공자를 추가할 때마다 이 분기를 찾아 고쳐야 하고, 빠뜨리면
 *   "네이버 로그인 버튼은 있는데 눌러도 아무 일이 없는" 상태가 된다.
 *   여기처럼 두면 새 클라이언트를 @Component 로 만들기만 해도 자동으로 등록된다.
 */
@Component
public class OAuthClients {

    private final Map<Provider, OAuthClient> clients = new EnumMap<>(Provider.class);

    public OAuthClients(List<OAuthClient> clientList) {
        for (OAuthClient client : clientList) {
            clients.put(client.provider(), client);
        }
    }

    /**
     * 제공자에 맞는 클라이언트를 돌려준다.
     *
     * 설정이 비어 있으면 없는 것과 같이 취급한다.
     * 키 없이 호출하면 제공자 쪽에서 알 수 없는 오류가 돌아오는데,
     * 그보다는 "아직 준비되지 않았다" 고 우리가 먼저 말하는 편이 낫다.
     */
    public OAuthClient get(Provider provider) {
        OAuthClient client = clients.get(provider);

        if (client == null) {
            throw new IllegalArgumentException("지원하지 않는 로그인 방식입니다: " + provider);
        }
        if (!client.isConfigured()) {
            throw new IllegalStateException(provider + " 로그인이 아직 설정되지 않았습니다.");
        }
        return client;
    }
}
