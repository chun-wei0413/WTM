package com.memehub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MemehubApplication {

    public static void main(String[] args) {
        SpringApplication.run(MemehubApplication.class, args);
    }
}
