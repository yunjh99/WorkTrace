package com.example.marketbot.global.config;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * ✅ 왜 필요한가?
 * - Slack Web API(views.open 등)를 호출하려면 HTTP 클라이언트가 필요함.
 * - RestTemplate을 스프링 빈으로 등록해두면 SlackClient에서 주입받아 사용 가능.
 * - 나중에 WebClient로 바꿔도 이 계층만 바꾸면 됨.
 */
@Configuration
public class HttpConfig {

    @Bean
    public RestTemplate restTemplate(
            @Value("${external-api.connect-timeout:3s}") Duration connectTimeout,
            @Value("${external-api.read-timeout:5s}") Duration readTimeout
    ) {
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(connectTimeout.toMillis()))
                .build();

        PoolingHttpClientConnectionManager connectionManager =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setDefaultConnectionConfig(connectionConfig)
                        .build();

        RequestConfig requestConfig = RequestConfig.custom()
                .setResponseTimeout(Timeout.ofMilliseconds(readTimeout.toMillis()))
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();

        // PATCH 요청과 명시적인 연결·응답 timeout을 지원하는 Apache HttpClient 사용
        HttpComponentsClientHttpRequestFactory requestFactory =
                new HttpComponentsClientHttpRequestFactory(httpClient);

        return new RestTemplate(requestFactory);
    }
}
