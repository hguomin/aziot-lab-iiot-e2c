package com.aziot.cloudapp;

import java.util.function.Function;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class AzureIoTApplication {
    public AzureIoTApplication() {
        System.out.println("AzureIoTApplication is created.");
    }
    public static void main(String[] args) {
        SpringApplication.run(AzureIoTApplication.class, args);
    }

    @Bean(name="HttpExample")
    public Function<String, String> httpExample() {
        return name -> {
            return "Hello " + name;
        };
    }
}