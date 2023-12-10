package com.aerotrack.lambda;

import com.aerotrack.lambda.workflow.AirportsRefreshWorkflow;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;


@Slf4j
public class RefreshRequestHandler implements RequestHandler<ScheduledEvent, Void> {


    public Void handleRequest(ScheduledEvent event, Context context) {


        try {
            AirportsRefreshWorkflow.create().refreshFlights();
        }
        catch (IOException | NullPointerException exc) {
            log.error("An exception occurred: " + exc);
        }

        return null;
    }
}


