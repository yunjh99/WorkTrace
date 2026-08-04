package com.example.marketbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * MarketBot을 시작하는 진입점입니다.
 * 비동기 처리를 활성화하여 Slack의 짧은 응답 제한과 오래 걸리는 조회 작업을 분리합니다.
 */
@EnableAsync
@SpringBootApplication
public class MarketBotApplication {
    public static void main(String[] args) { SpringApplication.run(MarketBotApplication.class, args); }
}
