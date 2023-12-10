package com.aerotrack.lambda;

import com.aerotrack.lambda.workflow.RefreshWorkflow;
import com.aerotrack.model.entities.Airport;
import com.aerotrack.utils.clients.ryanair.RyanairClient;
import com.aerotrack.utils.clients.s3.AerotrackS3Client;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class RefreshLambdaTest {


    private RefreshWorkflow workflow;
    @Mock
    private RyanairClient ryanairClient;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        when(ryanairClient.getAirportConnections(any())).thenReturn(List.of("VLC", "NAP"));
        workflow = new RefreshWorkflow(null, ryanairClient);
    }


    @Test
    void updateAirports_SuccessfulRequest_CorrectUpdate() {

        assertDoesNotThrow(() -> {

            List<Airport> airports = new ArrayList<>();
            Airport a = Airport.builder()
                    .airportCode("TSF")
                    .connections(List.of("DUB"))
                    .build();

            airports.add(a);
            airports = workflow.updateAirports(airports, a);

            assertEquals(airports.size(), 1);
            assertEquals(airports.get(0).getConnections().size(), 2);
            assert(!airports.get(0).getConnections().contains("DUB"));
        }, "Should not have thrown any exceptions.");
    }

    @Test
    void updateAirports_SuccessfulRequest_CorrectAdd() {

        assertDoesNotThrow(() -> {

            Airport a = Airport.builder()
                    .airportCode("FRA")
                    .build();

            List<Airport> airports = workflow.updateAirports(new ArrayList<>(), a);
            assertEquals(airports.size(), 1);
            assertEquals(airports.get(0).getAirportCode(), "FRA");
        }, "Should not have thrown any exceptions.");
    }


    @Test
    void chooseAirport_ReturnsLastUpdatedAirport() throws IOException {

        Airport savedAirport1 = Airport.builder()
                .airportCode("TSF")
                .lastUpdatedDateTime("2023-12-01T10:00:00.000Z")
                .build();
        Airport savedAirport2 = Airport.builder()
                .airportCode("VLC")
                .lastUpdatedDateTime("2022-12-01T09:00:00.000Z")
                .build();

        List<Airport> savedAirports = List.of(savedAirport1, savedAirport2);

        List<Airport> allAirports = List.of(
                Airport.builder().airportCode("TSF").build()
        );

        // Choose airport from saved airports
        Airport chosenAirport = workflow.chooseAirport(savedAirports, allAirports);

        assertNotNull(chosenAirport);
        assertEquals("VLC", chosenAirport.getAirportCode());
    }

}
