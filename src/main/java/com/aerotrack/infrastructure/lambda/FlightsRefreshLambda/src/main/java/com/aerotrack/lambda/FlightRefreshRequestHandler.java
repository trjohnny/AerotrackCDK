package com.aerotrack.lambda;

import com.aerotrack.lambda.workflow.FlightRefreshWorkflow;
import com.aerotrack.utils.clients.api.currencyConverter.CurrencyConverter;
import com.aerotrack.utils.clients.api.ryanair.RyanairClient;
import com.aerotrack.utils.clients.s3.AerotrackS3Client;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;

import java.io.IOException;
// FLIGHT_TABLE AIRPORTS_BUCKET

@Slf4j
public class FlightRefreshRequestHandler implements RequestHandler<ScheduledEvent, Void> {
    private final AerotrackS3Client s3Client = AerotrackS3Client.create();
    private final DynamoDbEnhancedClient dynamoDbEnhancedClient = DynamoDbEnhancedClient.create();
    private final RyanairClient ryanairClient = RyanairClient.create();
    private final CurrencyConverter currencyConverter = CurrencyConverter.create();


    public Void handleRequest(ScheduledEvent event, Context context) {
        FlightRefreshWorkflow flightRefreshWorkflow = new FlightRefreshWorkflow(s3Client, dynamoDbEnhancedClient, ryanairClient, currencyConverter);

        try {
            flightRefreshWorkflow.refreshFlights();
        } catch (IOException | NullPointerException | InterruptedException exc) {
            log.error("An exception occurred: " + exc);
        }

        return null;
    }
}


