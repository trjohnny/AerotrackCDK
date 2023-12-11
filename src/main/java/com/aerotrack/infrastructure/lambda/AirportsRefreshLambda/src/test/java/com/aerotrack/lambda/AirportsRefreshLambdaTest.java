package com.aerotrack.lambda;

import com.aerotrack.lambda.workflow.AirportsRefreshWorkflow;
import com.aerotrack.model.entities.Airport;
import com.aerotrack.utils.clients.ryanair.RyanairClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class AirportsRefreshLambdaTest {


    private AirportsRefreshWorkflow workflow;
    @Mock
    private RyanairClient ryanairClient;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        when(ryanairClient.getAirportConnections(any())).thenReturn(List.of("VLC", "NAP"));
        workflow = new AirportsRefreshWorkflow(null, ryanairClient);
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
