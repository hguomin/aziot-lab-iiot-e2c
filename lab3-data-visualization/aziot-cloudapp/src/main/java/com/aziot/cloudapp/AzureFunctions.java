package com.aziot.cloudapp;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.annotation.Cardinality;
import com.microsoft.azure.functions.annotation.EventHubTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.azure.functions.signalr.SignalRConnectionInfo;
import com.microsoft.azure.functions.signalr.SignalRMessage;
import com.microsoft.azure.functions.signalr.annotation.SignalRConnectionInfoInput;
import com.microsoft.azure.functions.signalr.annotation.SignalROutput;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.springframework.cloud.function.adapter.azure.FunctionInvoker;
/**
* Azure Functions with HTTP Trigger.
*/
public class AzureFunctions extends FunctionInvoker<String, String> {
    /**
    * This function listens at endpoint "/api/HttpExample". Two ways to invoke it using "curl" command in bash:
    * 1. curl -d "HTTP Body" {your host}/api/HttpExample
    * 2. curl "{your host}/api/HttpExample?name=HTTP%20Query"
    */
    @FunctionName("HttpExample")
    public HttpResponseMessage run(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.GET, HttpMethod.POST},
                authLevel = AuthorizationLevel.ANONYMOUS)
                HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        context.getLogger().info("Java HTTP trigger processed a request.");

        // Parse query parameter
        final String query = request.getQueryParameters().get("name");
        final String name = request.getBody().orElse(query);

        if (name == null) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("Please pass a name on the query string or in the request body").build();
        } else {
            return request.createResponseBuilder(HttpStatus.OK).body(handleRequest(name, context)).build();
        }
    }

    @FunctionName("index")
    public HttpResponseMessage index(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.GET, HttpMethod.POST},
                authLevel = AuthorizationLevel.ANONYMOUS)
                HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) throws IOException {
        context.getLogger().info("Java HTTP trigger processed a request.");

        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("public/index.html");
        String text = IOUtils.toString(inputStream, StandardCharsets.UTF_8.name());

        return request.createResponseBuilder(HttpStatus.OK).header("Content-Type", "text/html").body(text).build();
    }

    /**
     * This function will be invoked when an event is received from Event Hub.
     */
    @FunctionName("ReceiveIoTHubMessages")
    @SignalROutput(name = "$return", hubName = "serverless")
    public SignalRMessage receiveIoTHubMessages(
        @EventHubTrigger(name = "message", eventHubName = "", connection = "IoTHubBuiltinEndpoint", consumerGroup = "$Default", cardinality = Cardinality.ONE) String message,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java Event Hub trigger function executed.");
        context.getLogger().info("Message: " + message);
        return new SignalRMessage("newMessage", message);
    }

    @FunctionName("negotiate")
    public SignalRConnectionInfo negotiate(
            @HttpTrigger(
                name = "req",
                methods = { HttpMethod.POST },
                authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> req,
            @SignalRConnectionInfoInput(
                name = "connectionInfo",
                hubName = "serverless") SignalRConnectionInfo connectionInfo) {

        return connectionInfo;
    }
}
