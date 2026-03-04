package com.fourimpact.sdpsinkconnector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class SdpSinkConnectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SdpSinkConnectorApplication.class, args);
    }
}
