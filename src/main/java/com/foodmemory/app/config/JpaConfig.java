package com.foodmemory.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA 관련 설정.
 *
 * 설정 3층 구조에서 마지막 층에 해당한다.
 *   1층 build.gradle     무엇을 쓸 것인가
 *   2층 application.yml  어디에 붙을 것인가
 *   3층 이 클래스         어떻게 동작할 것인가
 *
 * BaseEntity 에 @CreatedDate / @LastModifiedDate 를 붙이는 것만으로는 동작하지 않는다.
 * 이 기능을 켜겠다는 선언이 따로 있어야 하고, 그게 @EnableJpaAuditing 이다.
 * 어노테이션만 붙이고 이 클래스를 빼먹으면 값이 채워지지 않아
 * NOT NULL 위반으로 저장이 실패한다.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
