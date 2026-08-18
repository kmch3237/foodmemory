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
     * 처음에는 "갤러리와 상세는 누구나 본다" 를 전제로 필요한 곳만 막았다.
     * 공유 공간이 생기면서 그 전제가 바뀌었다. 이제 모든 기록에 주인이 있고,
     * 개인 기록은 작성자만, 공간 기록은 참여자만 본다. 그래서 목록이 늘었다.
     *
     * 이 방식의 대가는 분명하다. 새 화면을 추가하면서 이 목록에 넣는 것을 잊으면
     * 그 화면은 로그인 없이 열린다. 화면을 추가할 때 이 목록을 같이 보는 것을 규칙으로 삼는다.
     *
     * 언젠가 "공개 기록" 이 생기면 그때는 반대로 뒤집는 편이 안전하다.
     * 지금은 공개가 없어서, 뒤집으면 로그인·가입 화면까지 막히는 실수가 더 위험하다.
     *
     * "/posts" 는 업로드(POST /posts)를 가리킨다. 내 갤러리는 "/" 라서 겹치지 않고,
     * 그 화면은 컨트롤러가 직접 로그인 화면으로 보낸다.
     * "*" 는 경로 한 칸, "**" 는 그 아래 전부를 뜻한다.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginCheckInterceptor())
                .order(1)
                .addPathPatterns(
                        "/posts",                  // 업로드 처리
                        "/posts/*",                // 상세·업로드 폼·다음 페이지
                        "/posts/*/edit",           // 수정 폼·수정 저장
                        "/posts/*/delete",         // 삭제
                        "/posts/*/comments/**",    // 댓글 등록·수정·삭제
                        "/posts/*/places",         // 장소 후보 조회
                        "/posts/*/place",          // 장소 지정
                        "/spaces",                 // 공간 목록·생성
                        "/spaces/**",              // 공간 화면·참여·초대 코드
                        "/account"                 // 계정 설정
                );
    }
}
