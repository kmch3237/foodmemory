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
 * 구글 로그인.
 *
 * 카카오와 흐름은 같고 주소와 응답 필드 이름만 다르다.
 * 그 차이를 이 클래스 안에 가둬두는 것이 OAuthClient 인터페이스의 목적이다.
 *
 * 주소는 구글이 공개하는 설정 문서에 적힌 값을 그대로 쓴다.
 *   https://accounts.google.com/.well-known/openid-configuration
 */
@Slf4j
@Component
public class GoogleOAuthClient implements OAuthClient {

    private static final String AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_URL = "https://openidconnect.googleapis.com/v1/userinfo";

    /**
     * 무엇을 받아올지 미리 밝힌다.
     *   openid  : 로그인 용도로 쓰겠다는 표시
     *   email   : 이메일
     *   profile : 이름 등 기본 프로필
     * 필요 없는 것까지 넣으면 동의 화면이 길어지고 사용자가 중간에 그만둔다.
     */
    private static final String SCOPE = "openid email profile";

    private final RestClient restClient = RestClient.create();

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public GoogleOAuthClient(@Value("${app.oauth.google.client-id:}") String clientId,
                             @Value("${app.oauth.google.client-secret:}") String clientSecret,
                             @Value("${app.oauth.google.redirect-uri:}") String redirectUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    @Override
    public Provider provider() {
        return Provider.GOOGLE;
    }

    /** 구글은 카카오와 달리 client secret 이 항상 필요하다. */
    @Override
    public boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank() && !redirectUri.isBlank();
    }

    @Override
    public String authorizeUrl(String state) {
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPE)
                .queryParam("state", state)

                /*
                 * 이미 구글에 로그인돼 있어도 다시 인증받게 한다.
                 *
                 * 카카오처럼 prompt=login 을 쓸 수 없다.
                 * 구글이 문서에 밝힌 prompt 값은 none / consent / select_account 셋뿐이다.
                 *
                 * 그래서 OpenID Connect 표준인 max_age 를 쓴다.
                 * "이 시간(초) 안에 인증한 사람만 인정하겠다" 는 뜻이라,
                 * 0 을 주면 어떤 과거 인증도 인정되지 않아 다시 비밀번호를 묻게 된다.
                 *
                 * select_account 도 함께 준다. max_age 를 무시하는 경우에도
                 * 최소한 계정 선택 화면은 거치게 되어 조용히 통과하지는 않는다.
                 */
                .queryParam("prompt", "select_account")
                .queryParam("max_age", 0)

                .build()
                // scope 의 공백을 %20 으로 바꿔야 한다. 인코딩하지 않으면 주소가 깨진다
                .encode()
                .toUriString();
    }

    @Override
    public OAuthUserInfo fetchUserInfo(String code) {
        String accessToken = exchangeToken(code);
        return fetchProfile(accessToken);
    }

    private String exchangeToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("code", code);

        JsonNode body;
        try {
            body = restClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);

        } catch (RestClientException e) {
            // 이미 쓴 인가 코드로 다시 들어오는 경우가 대부분이다.
            // 자세한 내용은 KakaoOAuthClient 의 같은 자리에 적어두었다.
            log.warn("구글 토큰 교환에 실패했습니다.", e);
            throw new IllegalStateException("구글 로그인에 실패했습니다.");
        }

        if (body == null || !body.hasNonNull("access_token")) {
            throw new IllegalStateException("구글 토큰을 받지 못했습니다.");
        }
        return body.get("access_token").asText();
    }

    /**
     * 구글의 고유 식별자는 'sub' 다.
     *
     * 이메일을 식별자로 쓰지 않는 이유:
     *   회사 계정처럼 이메일 주소가 바뀔 수 있다. 그러면 같은 사람인데 다른 회원이 되어
     *   그동안 쌓은 기록에서 잘려나간다. sub 는 계정이 살아 있는 한 바뀌지 않는다.
     */
    private OAuthUserInfo fetchProfile(String accessToken) {
        JsonNode body;
        try {
            body = restClient.get()
                    .uri(USERINFO_URL)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);

        } catch (RestClientException e) {
            log.warn("구글 사용자 정보 조회에 실패했습니다.", e);
            throw new IllegalStateException("구글 로그인에 실패했습니다.");
        }

        if (body == null || !body.hasNonNull("sub")) {
            throw new IllegalStateException("구글 사용자 정보를 받지 못했습니다.");
        }

        return new OAuthUserInfo(
                body.get("sub").asText(),
                body.path("name").asText(null),
                body.path("email").asText(null)
        );
    }
}
