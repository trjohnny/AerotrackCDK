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
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
            scanQueryRequest.validate();
            log.info("QueryRequestHandler started with request [{}]", scanQueryRequest);

            Integer minDays = scanQueryRequest.getMinDays();
            Integer maxDays = scanQueryRequest.getMaxDays();

            String availabilityStart = scanQueryRequest.getAvailabilityStart();
            String availabilityEnd = scanQueryRequest.getAvailabilityEnd();

            List<String> departureAirports = scanQueryRequest.getDepartureAirports();
            List<String> destinationAirports = scanQueryRequest.getDestinationAirports();

            Integer maxChanges = Optional.ofNullable(scanQueryRequest.getMaxChanges()).orElse(0);

            // This will be used when flight connections will be implemented
            Optional<Integer> minTimeBetweenChangesHours = Optional.ofNullable(scanQueryRequest.getMinTimeBetweenChangesHours());
            Optional<Integer> maxTimeBetweenChangesHours = Optional.ofNullable(scanQueryRequest.getMaxTimeBetweenChangesHours());

            Boolean returnToSameAirport = Optional.ofNullable(scanQueryRequest.getReturnToSameAirport()).orElse(true);

            ScanQueryResponse scanQueryResponse = queryLambdaWorkflow.queryAndProcessFlights(minDays, maxDays, availabilityStart,
                    availabilityEnd, departureAirports, destinationAirports, maxChanges, minTimeBetweenChangesHours,
                    maxTimeBetweenChangesHours, returnToSameAirport);

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
            String errorMessage = e.getMessage();
            Throwable cause = e.getCause();
            String causeMessage = (cause != null) ? cause.getMessage() : "No cause available";

            log.error("Got exception: " + errorMessage + ". Cause: " + causeMessage);

            response.setStatusCode(500); // Internal Server Error
            response.setHeaders(Map.of("Content-Type", "application/json"));

            String responseBody = String.format(
                    "{\"error\": \"%s\", \"cause\": \"%s\"}",
                    errorMessage.replace("\"", "\\\""), // Escape quotes in the error message
                    causeMessage.replace("\"", "\\\"")  // Escape quotes in the cause message
            );

            response.setBody(responseBody);
        }

        return response;
    }
}
