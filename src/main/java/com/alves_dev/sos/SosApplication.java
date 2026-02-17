package com.alves_dev.sos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class SosApplication {

    public static void main(String[] args) {
        SpringApplication.run(SosApplication.class, args);
    }

}
