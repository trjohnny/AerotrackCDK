package com.aerotrack.lambda;

import com.aerotrack.lambda.workflow.QueryLambdaWorkflow;
import com.aerotrack.model.ScanQueryRequest;
import com.aerotrack.model.ScanQueryResponse;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.Map;

@Slf4j
public class QueryRequestHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final DynamoDbEnhancedClient dynamoDbEnhancedClient = DynamoDbEnhancedClient.create();
    private final S3Client s3Client = S3Client.create();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final QueryLambdaWorkflow queryLambdaWorkflow = new QueryLambdaWorkflow(dynamoDbEnhancedClient, s3Client);

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();

        try {
            ScanQueryRequest scanQueryRequest = objectMapper.readValue(request.getBody(), ScanQueryRequest.class);

            ScanQueryResponse scanQueryResponse = queryLambdaWorkflow.queryAndProcessFlights(scanQueryRequest);

            response.setStatusCode(200);
            response.setHeaders(Map.of("Content-Type", "application/json"));
            response.setBody(objectMapper.writeValueAsString(scanQueryResponse));
        } catch (Exception e) {
            log.error(e.getMessage());
            response.setStatusCode(500);
            response.setHeaders(Map.of("Content-Type", "application/json"));
            response.setBody("{\"error\": \"" + e.getMessage() + "\"}");
        }
        return response;
    }
}
