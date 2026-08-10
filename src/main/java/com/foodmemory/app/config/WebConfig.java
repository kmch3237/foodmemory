package com.foodmemory.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

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

    public WebConfig(@Value("${app.upload.path}") String uploadPath,
                     @Value("${app.upload.url-prefix}") String urlPrefix) {
        this.uploadPath = uploadPath;
        this.urlPrefix = urlPrefix;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = "file:" + Paths.get(uploadPath).toAbsolutePath().normalize() + "/";

        registry.addResourceHandler(urlPrefix + "/**")   // /uploads/** 로 들어오는 요청을
                .addResourceLocations(location);          // 이 폴더에서 찾는다
    }
}
