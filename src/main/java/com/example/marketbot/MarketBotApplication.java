package com.example.marketbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class MarketBotApplication {
    public static void main(String[] args) { SpringApplication.run(MarketBotApplication.class, args); }
}
