package com.aerotrack.lambda;

import com.aerotrack.lambda.workflow.RefreshWorkflow;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;


public class RefreshRequestHandler implements RequestHandler<ScheduledEvent, Void> {


    public Void handleRequest(ScheduledEvent event, Context context) {

        LambdaLogger logger = context.getLogger();

        try {
            new RefreshWorkflow(S3Client.create(), DynamoDbEnhancedClient.create()).handleRequest(logger);
        }
        catch (IOException | NullPointerException | InterruptedException exc) {
            logger.log("An exception occurred: " + exc);
        }

        return null;
    }
}


