package com.aerotrack.lambda;

import com.aerotrack.lambda.workflow.RefreshWorkflow;
import com.aerotrack.utils.clients.s3.AerotrackS3Client;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;

import java.io.IOException;


@Slf4j
public class RefreshRequestHandler implements RequestHandler<ScheduledEvent, Void> {


    public Void handleRequest(ScheduledEvent event, Context context) {


        try {
            new RefreshWorkflow(AerotrackS3Client.create(), DynamoDbEnhancedClient.create()).refreshFlights();
        }
        catch (IOException | NullPointerException | InterruptedException exc) {
            log.error("An exception occurred: " + exc);
        }

        return null;
    }
}


