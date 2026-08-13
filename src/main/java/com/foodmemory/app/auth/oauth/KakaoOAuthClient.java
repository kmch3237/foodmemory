package com.foodmemory.app.auth.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.foodmemory.app.entity.Provider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 카카오 로그인.
 *
 * 인가는 kauth.kakao.com, 사용자 정보 조회는 kapi.kakao.com 으로 호스트가 다르다.
 * 하나로 착각하면 404 가 나는데 원인을 찾기 어려우니 상수로 나눠 적어둔다.
 */
@Slf4j
@Component
public class KakaoOAuthClient implements OAuthClient {

    private static final String AUTH_HOST = "https://kauth.kakao.com";
    private static final String API_HOST = "https://kapi.kakao.com";

    private final RestClient restClient = RestClient.create();

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public KakaoOAuthClient(@Value("${app.oauth.kakao.client-id:}") String clientId,
                            @Value("${app.oauth.kakao.client-secret:}") String clientSecret,
                            @Value("${app.oauth.kakao.redirect-uri:}") String redirectUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    @Override
    public Provider provider() {
        return Provider.KAKAO;
    }

    @Override
    public boolean isConfigured() {
        return !clientId.isBlank() && !redirectUri.isBlank();
    }

    @Override
    public String authorizeUrl(String state) {
        return UriComponentsBuilder.fromUriString(AUTH_HOST + "/oauth/authorize")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")   // 고정값이다
                .queryParam("state", state)

                /*
                 * 이미 카카오에 로그인돼 있어도 다시 인증받게 한다.
                 *
                 * 이게 없으면 브라우저에 남아 있는 카카오 로그인 상태를 그대로 인정해서,
                 * 버튼을 누르는 순간 아무것도 묻지 않고 통과한다.
                 * 남이 내 노트북을 잠깐 쓰면 그대로 내 계정에 들어갈 수 있다는 뜻이다.
                 *
                 * prompt=login 은 그 상태를 무시하고 카카오 로그인 화면을 다시 띄운다.
                 * 카카오톡 인앱 브라우저에서는 지원되지 않는다.
                 */
                .queryParam("prompt", "login")

                .build()
                .toUriString();
    }

    @Override
    public OAuthUserInfo fetchUserInfo(String code) {
        String accessToken = exchangeToken(code);
        return fetchProfile(accessToken);
    }

    /**
     * 인가 코드를 액세스 토큰으로 바꾼다.
     *
     * redirect_uri 를 여기서도 보내는 이유:
     *   카카오는 인가 코드를 발급할 때 쓴 주소와 같은지 대조한다.
     *   코드를 가로챈 공격자가 자기 서버 주소로 토큰을 받아가는 것을 막기 위해서다.
     *   그래서 두 요청의 redirect_uri 는 글자 하나까지 같아야 한다.
     */
    private String exchangeToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        if (!clientSecret.isBlank()) {
            // 콘솔에서 '보안 - Client Secret' 을 켠 경우에만 필요하다.
            // 켜지 않았는데 보내면 오히려 거부당하므로 값이 있을 때만 넣는다.
            form.add("client_secret", clientSecret);
        }

        JsonNode body;
        try {
            body = restClient.post()
                    .uri(AUTH_HOST + "/oauth/token")
                    // 카카오 토큰 요청은 JSON 이 아니라 폼 형식이다. 이걸 틀리면 400 이 온다
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);

        } catch (RestClientException e) {
            /*
             * 카카오가 4xx·5xx 를 돌려주면 RestClient 는 예외를 던진다.
             * 그대로 두면 우리 예외가 아닌 것이 위층까지 올라가 500 화면이 된다.
             *
             * 여기서 실패하는 것은 드문 일이 아니다.
             * 인가 코드는 한 번만 쓸 수 있고 수명이 짧아서, 사용자가 새로고침하거나
             * 뒤로가기로 콜백 주소를 다시 열기만 해도 이미 쓴 코드가 되어 거부당한다.
             * 사용자 잘못이 아니므로 다시 시도하라고 안내할 수 있어야 한다.
             */
            log.warn("카카오 토큰 교환에 실패했습니다.", e);
            throw new IllegalStateException("카카오 로그인에 실패했습니다.");
        }

        if (body == null || !body.hasNonNull("access_token")) {
            throw new IllegalStateException("카카오 토큰을 받지 못했습니다.");
        }
        return body.get("access_token").asText();
    }

    /**
     * 토큰으로 사용자 정보를 가져온다.
     *
     * 닉네임과 이메일이 없을 수 있다. 카카오에서 동의 항목이 선택으로 설정돼 있으면
     * 사용자가 동의하지 않은 항목은 응답에 아예 담기지 않는다.
     * 그래서 값이 온다고 가정하지 않고 path() 로 안전하게 꺼낸다.
     */
    private OAuthUserInfo fetchProfile(String accessToken) {
        JsonNode body;
        try {
            body = restClient.get()
                    .uri(API_HOST + "/v2/user/me")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);

        } catch (RestClientException e) {
            log.warn("카카오 사용자 정보 조회에 실패했습니다.", e);
            throw new IllegalStateException("카카오 로그인에 실패했습니다.");
        }

        if (body == null || !body.hasNonNull("id")) {
            throw new IllegalStateException("카카오 사용자 정보를 받지 못했습니다.");
        }

        // id 는 숫자로 오지만 문자열로 저장한다. 계산할 일이 없는 식별자다.
        String providerUserId = body.get("id").asText();

        JsonNode account = body.path("kakao_account");
        String nickname = account.path("profile").path("nickname").asText(null);
        String email = account.path("email").asText(null);

        return new OAuthUserInfo(providerUserId, nickname, email);
    }
}
