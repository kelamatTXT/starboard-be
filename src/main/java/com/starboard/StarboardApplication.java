package com.starboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StarboardApplication {
    public static void main(String[] args) {
        SpringApplication.run(StarboardApplication.class, args);
    }
}
