package com.eagle.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Eagle演示应用启动类（order-service，dev环境）
 */
@SpringBootApplication
public class EagleDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(EagleDemoApplication.class, args);
    }
}
