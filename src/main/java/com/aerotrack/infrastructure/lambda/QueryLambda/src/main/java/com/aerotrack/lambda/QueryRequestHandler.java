package com.aerotrack.lambda;

import com.aerotrack.lambda.workflow.QueryLambdaWorkflow;
import com.aerotrack.model.protocol.ScanQueryRequest;
import com.aerotrack.model.protocol.ScanQueryResponse;
import com.aerotrack.utils.clients.dynamodb.AerotrackDynamoDbClient;
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
public class QueryRequestHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final AerotrackS3Client s3Client = AerotrackS3Client.create();
    private final AerotrackDynamoDbClient dynamoDbClient = AerotrackDynamoDbClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final QueryLambdaWorkflow queryLambdaWorkflow = new QueryLambdaWorkflow(dynamoDbClient, s3Client);

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();

        try {
            ScanQueryRequest scanQueryRequest = objectMapper.readValue(request.getBody(), ScanQueryRequest.class);
            scanQueryRequest.validate(); // Validate the request
            log.info("QueryRequestHandler started with request [{}]", scanQueryRequest);

            ScanQueryResponse scanQueryResponse = queryLambdaWorkflow.queryAndProcessFlights(scanQueryRequest);

            response.setStatusCode(200);
            response.setHeaders(Map.of(
                    "Content-Type", "application/json",
                    "Access-Control-Allow-Origin", "*",
                    "Access-Control-Allow-Methods", "OPTIONS,POST"));
            response.setBody(objectMapper.writeValueAsString(scanQueryResponse));
        } catch (IllegalArgumentException e) {
            log.error("Validation error: " + e.getMessage());
            response.setStatusCode(400); // Bad Request
            response.setHeaders(Map.of("Content-Type", "application/json"));
            response.setBody("{\"error\": \"" + e.getMessage() + "\"}");
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
