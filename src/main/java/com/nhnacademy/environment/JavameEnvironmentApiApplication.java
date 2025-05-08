package com.nhnacademy.environment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableFeignClients(basePackages = "com.nhnacademy.environment.member.client")
public class JavameEnvironmentApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(JavameEnvironmentApiApplication.class, args);
    }

}
