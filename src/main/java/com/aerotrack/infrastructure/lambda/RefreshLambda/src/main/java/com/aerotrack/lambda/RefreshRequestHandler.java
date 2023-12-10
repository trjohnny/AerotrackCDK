package com.aerotrack.lambda;

import com.aerotrack.lambda.workflow.RefreshWorkflow;
import com.aerotrack.utils.clients.dynamodb.AerotrackDynamoDbClient;
import com.aerotrack.utils.clients.ryanair.RyanairClient;
import com.aerotrack.utils.clients.s3.AerotrackS3Client;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;

import java.io.IOException;


@Slf4j
public class RefreshRequestHandler implements RequestHandler<ScheduledEvent, Void> {
    private final AerotrackS3Client s3Client = AerotrackS3Client.create();
    private final DynamoDbEnhancedClient dynamoDbEnhancedClient = DynamoDbEnhancedClient.create();
    private final RyanairClient ryanairClient = RyanairClient.create();


    public Void handleRequest(ScheduledEvent event, Context context) {
        RefreshWorkflow refreshWorkflow = new RefreshWorkflow(s3Client, dynamoDbEnhancedClient, ryanairClient);

        try {
            refreshWorkflow.refreshFlights();
        } catch (IOException | NullPointerException | InterruptedException exc) {
            log.error("An exception occurred: " + exc);
        }

        return null;
    }
}


