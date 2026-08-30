package com.example.messengerservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class MessengerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                MessengerServiceApplication.class,
                args
        );
    }
}