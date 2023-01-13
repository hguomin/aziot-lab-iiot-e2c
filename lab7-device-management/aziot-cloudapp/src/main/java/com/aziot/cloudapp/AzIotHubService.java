package com.aziot.cloudapp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.microsoft.azure.sdk.iot.service.configurations.ConfigurationContent;
import com.microsoft.azure.sdk.iot.service.configurations.ConfigurationsClient;
import com.microsoft.azure.sdk.iot.service.exceptions.IotHubException;
import com.microsoft.azure.sdk.iot.service.methods.DirectMethodsClient;
import com.microsoft.azure.sdk.iot.service.query.QueryClient;
import com.microsoft.azure.sdk.iot.service.query.RawQueryResponse;
import com.microsoft.azure.sdk.iot.service.registry.Device;
import com.microsoft.azure.sdk.iot.service.registry.RegistryClient;
import com.microsoft.azure.sdk.iot.service.twin.Twin;
import com.microsoft.azure.sdk.iot.service.twin.TwinClient;
import com.microsoft.azure.sdk.iot.service.twin.TwinCollection;

@Service
public class AzIotHubService {
    private final RegistryClient registryClient;
    private final TwinClient twinClient;
    private final DirectMethodsClient directMethodsClient;
    private final QueryClient queryClient;
    private final ConfigurationsClient configurationsClient;
    @Autowired
    public AzIotHubService(RegistryClient registryClient, 
                           TwinClient twinClient, 
                           DirectMethodsClient directMethodsClient, 
                           QueryClient queryClient, 
                           ConfigurationsClient configurationsClient) {
        this.registryClient = registryClient;
        this.twinClient = twinClient;
        this.directMethodsClient = directMethodsClient;
        this.queryClient = queryClient;
        this.configurationsClient = configurationsClient;
    }

    public JsonArray getIoTEdgeDevices() {
        JsonArray devicesList = new JsonArray();

        try {
            Gson gson = new Gson();
            RawQueryResponse query = this.queryClient.queryRaw("SELECT * FROM devices");
            while(query.hasNext()) {
                JsonObject deviceQuery = gson.fromJson(query.next(), JsonObject.class);
                boolean isIoTEdgeDevice = deviceQuery.get("capabilities").getAsJsonObject().get("iotEdge").getAsBoolean();
                if (isIoTEdgeDevice) {
                    String deviceId = deviceQuery.get("deviceId").getAsString();
                    Device device = this.registryClient.getDevice(deviceId);

                    JsonObject deviceJson = new JsonObject();
                    deviceJson.addProperty("deviceId", deviceId);
                    deviceJson.addProperty("primaryKey", device.getPrimaryKey());
                    deviceJson.addProperty("status", device.getStatus().getValue());
                    deviceJson.addProperty("connectionState", device.getConnectionState().getValue());

                    devicesList.add(deviceJson);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return devicesList;
    } 

    public JsonObject deployIoTEdgeModule(String deviceId, String module, String settings) {

        Gson gson = new Gson();
        JsonObject result = new JsonObject();
        JsonObject deploymentSettings = gson.fromJson(settings, JsonObject.class);
        try {
            Map<String, Map<String, Object>> modulesContent = new HashMap<String, Map<String, Object>>();

            //Edge Agent
            Twin edgeAgentTwin = this.twinClient.get(deviceId, "$edgeAgent");
            TwinCollection edgeAgentDesiredProps = edgeAgentTwin.getDesiredProperties();

            TwinCollection modules = (TwinCollection)edgeAgentDesiredProps.get("modules");
            //Get existing modules
            modules.forEach((moduleName, moduleSetting) -> {
                try {
                    Twin moduleTwin = this.twinClient.get(deviceId, moduleName);
                    TwinCollection desiredProperties = new TwinCollection() {
                        {
                            put("properties.desired", moduleTwin.getDesiredProperties());
                        }
                    };
                    modulesContent.put(moduleName, desiredProperties);

                } catch ( IOException | IotHubException e) {
                    e.printStackTrace();
                }
            });

            TwinCollection imageSettings = new TwinCollection();
            imageSettings.put("image", deploymentSettings.get("imageUri").getAsString());
            imageSettings.put("createOptions", deploymentSettings.get("containerOptions").getAsString());
            TwinCollection moduleSettings = new TwinCollection();
            moduleSettings.put("status", deploymentSettings.get("desiredStatus").getAsString());
            moduleSettings.put("type", "docker");
            moduleSettings.put("version", "1.0");
            moduleSettings.put("restartPolicy", deploymentSettings.get("restartPolicy").getAsString());
            moduleSettings.put("startupOrder", deploymentSettings.get("startupOrder").getAsInt());
            moduleSettings.put("settings", imageSettings);

            modules.put(module, moduleSettings);

            TwinCollection registryCredentials = (TwinCollection)((TwinCollection)((TwinCollection)edgeAgentDesiredProps.get("runtime")).get("settings")).get("registryCredentials");
            registryCredentials.put("gmdevcr", new TwinCollection(){
                {
                    put("address", "gmdevcr.azurecr.io");
                    put("password", "KEoHXaDOq/37XyJzXg8nqwmFiPONhC34");
                    put("username", "gmdevcr");
                }
            });

            modulesContent.put("$edgeAgent", new TwinCollection() {
                {
                    put("properties.desired", edgeAgentDesiredProps);
                }
            });

            //Edge Hub
            Twin edgeHubTwin = this.twinClient.get(deviceId, "$edgeHub");
            TwinCollection edgeHubDesiredProps = edgeHubTwin.getDesiredProperties();
            modulesContent.put("$edgeHub", new TwinCollection() {
                {
                    put("properties.desired", edgeHubDesiredProps);
                }
            });

            //New Module
            TwinCollection moduleDesiredProps = gson.fromJson(deploymentSettings.get("moduleTwin").getAsString(), TwinCollection.class);
            modulesContent.put(module, new TwinCollection() {
                {
                    put("properties.desired", moduleDesiredProps);
                }
            });

            ConfigurationContent confContent = new ConfigurationContent();
            confContent.setModulesContent(modulesContent);

            //Start the deployment
            this.configurationsClient.applyConfigurationContentOnDevice(deviceId, confContent);

            result.addProperty("result", "success");

        } catch (Exception e) {
            result.addProperty("result", "failed");
            e.printStackTrace();
        }
        
        return result;
    }
}
