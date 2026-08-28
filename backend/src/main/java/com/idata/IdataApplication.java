package com.idata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IdataApplication {
    public static void main(String[] args) {
        SpringApplication.run(IdataApplication.class, args);
    }
}
