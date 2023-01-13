package com.aziot.cloudapp;

import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

@SpringBootApplication
public class AzureIoTApplication {

    //-----Lab6: Azure IoT Hub SDKs integration--------//
    //private AzIotHubService iotHubService;
    //@Autowired
    //public AzureIoTApplication(AzIotHubService iotHubService) {
    //    this.iotHubService = iotHubService;
    //}

    public static void main(String[] args) {
        SpringApplication.run(AzureIoTApplication.class, args);
    }

    @Bean(name="HttpExample")
    public Function<String, String> httpExample() {
        return name -> {
            return "Hello " + name;
        };
    }

    //---------Lab7: Get IoT edge devices list--------//
    @Bean(name="EdgeDevices")
    public Supplier<JsonArray> getIoTEdgeDevices(AzIotHubService iotHubService) {
        return () -> {
            return iotHubService.getIoTEdgeDevices();
        };
    }

    //---------Lab7: Deploy IoT edge module--------//
    @Bean(name = "deployIoTEdgeModule")
    public Function<String, String> deployIoTEdgeModule(AzIotHubService iotHubService) {
        return (settings) -> {
            JsonObject result = iotHubService.deployIoTEdgeModule("gm-dev-iotedge-device", "devplus-dev2", settings);
            String ss = result.toString();
            
            return result.toString();
        };
    }
}