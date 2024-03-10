package com.aerotrack.lambda;

import com.aerotrack.model.entities.AirportsJsonFile;
import com.aerotrack.common.Constants;
import com.aerotrack.utils.clients.s3.AerotrackS3Client;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;

@Slf4j
public class FetchAirportsRequestHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final AerotrackS3Client s3Client = AerotrackS3Client.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        String resourcePath = request.getPath(); // Get the resource path from the request
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        AirportsJsonFile airportsJsonFile;

        try {

            String s3ObjectName;
            if (resourcePath.contains("ryanair")) {
                airportsJsonFile = s3Client.getRyanairAirports();
            } else if (resourcePath.contains("wizzair")) {
                airportsJsonFile = s3Client.getWizzairAirports();
            } else if (resourcePath.contains("merged")) {
                airportsJsonFile = s3Client.getMergedAirports();
            } else {
                response.setStatusCode(400); // Bad Request
                response.setBody("{\"error\": \"Invalid airport group specified.\"}");
                return response;
            }

            response.setStatusCode(200);
            response.setHeaders(Map.of(
                    "Content-Type", "application/json",
                    "Access-Control-Allow-Origin", "*",
                    "Access-Control-Allow-Methods", "OPTIONS,POST"));
            response.setBody(objectMapper.writeValueAsString(airportsJsonFile));
        } catch (IOException e) {
            log.error("Error reading from S3: " + e.getMessage());
            response.setStatusCode(500); // Internal Server Error
            response.setHeaders(Map.of("Content-Type", "application/json"));
            response.setBody("{\"error\": \"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            log.error("Got exception: " + e.getMessage());
            response.setStatusCode(500); // Internal Server Error
            response.setHeaders(Map.of("Content-Type", "application/json"));
            response.setBody("{\"error\": \"" + e.getMessage() + "\"}");
        }
        return response;
    }
}
