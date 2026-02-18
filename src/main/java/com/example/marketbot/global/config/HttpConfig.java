package com.example.marketbot.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * ✅ 왜 필요한가?
 * - Slack Web API(views.open 등)를 호출하려면 HTTP 클라이언트가 필요함.
 * - RestTemplate을 스프링 빈으로 등록해두면 SlackClient에서 주입받아 사용 가능.
 * - 나중에 WebClient로 바꿔도 이 계층만 바꾸면 됨.
 */
@Configuration
public class HttpConfig {

    @Bean
    public RestTemplate restTemplate() {
        // ✅ PATCH 지원을 위해 Apache HttpClient 기반 RequestFactory 사용
        return new RestTemplate(new HttpComponentsClientHttpRequestFactory());
    }
}
