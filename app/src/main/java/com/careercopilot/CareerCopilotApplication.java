package com.careercopilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.function.context.config.ContextFunctionCatalogAutoConfiguration;

@SpringBootApplication(exclude = { ContextFunctionCatalogAutoConfiguration.class })
public class CareerCopilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(CareerCopilotApplication.class, args);
    }
}
