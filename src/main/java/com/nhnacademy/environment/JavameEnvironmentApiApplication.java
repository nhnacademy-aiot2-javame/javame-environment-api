package com.nhnacademy.environment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
//@EnableDiscoveryClient
public class JavameEnvironmentApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(JavameEnvironmentApiApplication.class, args);
    }

}
