package com.aerotrack.lambda;

import com.aerotrack.lambda.workflow.AirportsRefreshWorkflow;
import com.aerotrack.utils.clients.api.RyanairApiClient;
import com.aerotrack.utils.clients.s3.AerotrackS3Client;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;


@Slf4j
public class AirportsRefreshRequestHandler implements RequestHandler<ScheduledEvent, Void> {
    private final AerotrackS3Client s3Client = AerotrackS3Client.create();
    private final RyanairApiClient ryanairClient = RyanairApiClient.create();
    private final AirportsRefreshWorkflow airportsRefreshWorkflow = new AirportsRefreshWorkflow(s3Client, ryanairClient);

    public Void handleRequest(ScheduledEvent event, Context context) {

        try {
            airportsRefreshWorkflow.refreshFlights();
        }
        catch (IOException | NullPointerException exc) {
            log.error("An exception occurred: " + exc);
        }

        return null;
    }
}


