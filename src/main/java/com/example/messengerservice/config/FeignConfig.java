package com.example.messengerservice.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfig {

    @Value("${gateway.internal-secret}")
    private String gatewaySecret;

    @Bean
    public RequestInterceptor gatewaySecretInterceptor() {

        return requestTemplate -> {

            requestTemplate.header(
                    "X-Gateway-Secret",
                    gatewaySecret
            );
        };
    }
}