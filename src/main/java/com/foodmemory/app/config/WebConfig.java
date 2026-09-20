package com.foodmemory.app.config;

import com.foodmemory.app.auth.LoginArgumentResolver;
import com.foodmemory.app.auth.LoginCheckInterceptor;
import com.foodmemory.app.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 로그인 검사와 @Login 주입을 붙이는 자리.
 *
 * 설정 3층의 두 번째 파일이다.
 * JpaConfig 와 마찬가지로, 자바 코드로만 표현할 수 있는 설정이라 여기에 둔다.
 *
 * ── 업로드된 사진은 왜 여기 없나 ──
 *
 * 전에는 "/uploads/... 로 오면 이 폴더에서 찾아 내보내라" 는 규칙이 여기 있었다.
 * 그 통로에는 로그인 검사가 없어서, 주소만 알면 누구나 남의 사진을 받을 수 있었다.
 * 지금은 PhotoController 가 /photos/{번호} 로 받아 볼 권한을 확인한 뒤 내보낸다.
 *
 * 그 규칙을 여기서 지운 것은 실수가 아니라 이 작업의 핵심이다.
 * 되살리면 옛 주소가 다시 열려 검사를 우회하게 된다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final LoginArgumentResolver loginArgumentResolver;
    private final MemberRepository memberRepository;

    public WebConfig(LoginArgumentResolver loginArgumentResolver,
                     MemberRepository memberRepository) {
        this.loginArgumentResolver = loginArgumentResolver;
        this.memberRepository = memberRepository;
    }

    /**
     * 약관과 개인정보 처리방침. 주소와 화면을 바로 잇는다.
     *
     * 넘길 데이터가 없는 고정 문서라 컨트롤러를 따로 만들지 않는다.
     * 메서드 하나가 "이 템플릿을 보여줘" 한 줄뿐이라면, 그 한 줄은 여기서 쓰는 편이 짧다.
     *
     * 가입하기 전에 읽어야 하는 문서라 아래 로그인 검사 목록에 넣지 않는다.
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/terms").setViewName("legal/terms");
        registry.addViewController("/privacy").setViewName("legal/privacy");
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
        registry.addInterceptor(new LoginCheckInterceptor(memberRepository))
                .order(1)
                .addPathPatterns(
                        "/posts",                  // 업로드 처리
                        "/posts/*",                // 상세·업로드 폼·다음 페이지
                        "/posts/*/edit",           // 수정 폼·수정 저장
                        "/posts/*/delete",         // 삭제
                        "/posts/*/comments/**",    // 댓글 등록·수정·삭제
                        "/posts/*/places",         // 장소 후보 조회
                        "/posts/*/place",          // 장소 지정
                        "/photos/**",              // 사진 파일 (원본·작은 사본)
                        "/spaces",                 // 공간 목록·생성
                        "/spaces/**",              // 공간 화면·참여·초대 코드
                        "/account",                // 계정 설정
                        "/account/**"              // 탈퇴
                );
    }
}
