package com.example.activity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.example.activity.config.ServiceProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(ServiceProperties.class)
public class ActivityApplication {
    public static void main(String[] args) { SpringApplication.run(ActivityApplication.class, args); }
}
