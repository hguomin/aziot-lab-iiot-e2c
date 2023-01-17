package com.aziot.cloudapp;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.microsoft.azure.sdk.iot.service.auth.IotHubConnectionString;
import com.microsoft.azure.sdk.iot.service.auth.IotHubConnectionStringBuilder;
import com.microsoft.azure.sdk.iot.service.configurations.ConfigurationsClient;
import com.microsoft.azure.sdk.iot.service.methods.DirectMethodsClient;
import com.microsoft.azure.sdk.iot.service.query.QueryClient;
import com.microsoft.azure.sdk.iot.service.registry.RegistryClient;
import com.microsoft.azure.sdk.iot.service.twin.TwinClient;

@Configuration
public class AzuIotHubConfiguration {
    private final String iothubConnectionString = "[YOUR IOT HUB CONNECTION STRING]";

    @Bean
    public IotHubConnectionString iotHubConnectionString() {
        return IotHubConnectionStringBuilder.createIotHubConnectionString(this.iothubConnectionString);
    }

    @Bean
    public RegistryClient iotHubRegistryClient() {
        return new RegistryClient(this.iothubConnectionString);
    }

    @Bean
    public TwinClient iotHubTwinClient() {
        return new TwinClient(this.iothubConnectionString);
    }

    @Bean
    public DirectMethodsClient iotHubDirectMethodClient() {
        return new DirectMethodsClient(this.iothubConnectionString);
    }

    @Bean
    public QueryClient iotHubQueryClient() {
        return new QueryClient(this.iothubConnectionString);
    }

    @Bean
    public ConfigurationsClient iotHubConfigurationsClient() {
        return new ConfigurationsClient(this.iothubConnectionString);
    }
}
