package com.foodmemory.app.config;

import com.foodmemory.app.auth.LoginArgumentResolver;
import com.foodmemory.app.auth.LoginCheckInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;
import java.util.List;

/**
 * 업로드된 사진을 브라우저가 볼 수 있게 연결한다.
 *
 * 파일을 서버 폴더에 저장하기만 하면 브라우저는 그 파일에 접근할 수 없다.
 * "/uploads/... 로 요청이 오면 이 폴더에서 찾아서 내보내라" 는 규칙이 필요하다.
 *
 * 설정 3층의 두 번째 파일이다.
 * JpaConfig 와 마찬가지로, 자바 코드로만 표현할 수 있는 설정이라 여기에 둔다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String uploadPath;
    private final String urlPrefix;
    private final LoginArgumentResolver loginArgumentResolver;

    public WebConfig(@Value("${app.upload.path}") String uploadPath,
                     @Value("${app.upload.url-prefix}") String urlPrefix,
                     LoginArgumentResolver loginArgumentResolver) {
        this.uploadPath = uploadPath;
        this.urlPrefix = urlPrefix;
        this.loginArgumentResolver = loginArgumentResolver;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = "file:" + Paths.get(uploadPath).toAbsolutePath().normalize() + "/";

        registry.addResourceHandler(urlPrefix + "/**")   // /uploads/** 로 들어오는 요청을
                .addResourceLocations(location);          // 이 폴더에서 찾는다
    }

    /** @Login 파라미터를 채워줄 리졸버를 Spring 에 알린다. 등록하지 않으면 그냥 무시된다. */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginArgumentResolver);
    }

    /**
     * 로그인이 필요한 경로를 한 곳에 모아둔다.
     *
     * "전부 막고 예외를 뚫는" 방식이 아니라 "필요한 곳만 막는" 방식을 쓴 이유:
     *   이 서비스는 갤러리와 상세를 누구나 볼 수 있는 것이 기본이다.
     *   전부 막아두고 excludePathPatterns 로 뚫기 시작하면, 공개해야 할 화면을
     *   하나 빠뜨렸을 때 "로그인해야 볼 수 있는 갤러리" 가 되어버린다.
     *
     * 다만 이 선택에는 대가가 있다. 나중에 글을 수정하는 화면을 추가하면서
     * 이 목록에 넣는 것을 잊으면 그 화면은 로그인 없이 열린다.
     * 화면을 추가할 때 이 목록을 같이 보는 것을 규칙으로 삼는다.
     *
     * "/posts" 는 업로드(POST /posts)를 가리킨다. 갤러리는 "/" 라서 겹치지 않는다.
     * "*" 는 경로 한 칸, "**" 는 그 아래 전부를 뜻한다.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginCheckInterceptor())
                .order(1)
                .addPathPatterns(
                        "/posts",                  // 업로드 처리
                        "/posts/new",              // 업로드 폼
                        "/posts/*/delete",         // 삭제
                        "/posts/*/restaurants",    // 식당 후보 조회
                        "/posts/*/restaurant",     // 식당 지정
                        "/account"                 // 계정 설정
                );
    }
}
