package com.aerotrack.lambda;

import com.aerotrack.lambda.workflow.FlightRefreshWorkflow;
import com.aerotrack.model.entities.Airport;
import com.aerotrack.model.entities.Flight;
import com.aerotrack.model.entities.FlightList;
import com.aerotrack.utils.clients.api.CurrencyConverterApiClient;
import com.aerotrack.utils.clients.api.RyanairApiClient;
import com.aerotrack.utils.clients.api.WizzairApiClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import com.aerotrack.utils.clients.s3.AerotrackS3Client;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlightsRefreshLambdaTest {

    @Mock
    private AerotrackS3Client mockS3Client;
    @Mock
    private RyanairApiClient mockRyanairClient;
    @Mock
    private WizzairApiClient mockWizzairClient;
    @Mock
    private DynamoDbEnhancedClient mockDynamoDbClient;
    @Mock
    private DynamoDbTable<Flight> mockFlightsTable;
    @Mock
    private CurrencyConverterApiClient currencyConverter;
    private FlightRefreshWorkflow flightRefreshWorkflow;

    private final static String AIRPORT_JSON_STRING = """
            {
              "airports":
                [
                  {
                    "airportCode" : "VIE",
                    "name" : "Vienna",
                    "countryCode" : "AT",
                    "connections" : ["TSF"]
                  }
                ]
            }
            """;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);

        when(mockS3Client.getStringObjectFromS3(any())).thenReturn(AIRPORT_JSON_STRING);
        when(currencyConverter.getConversionFactor(any(), any())).thenReturn(1.0);
        flightRefreshWorkflow = new FlightRefreshWorkflow(mockS3Client, mockDynamoDbClient, mockRyanairClient, mockWizzairClient, currencyConverter);

    }

    @Test
    void getAvailableAirports_SuccessfulRequest_CorrectMapping() {

        assertDoesNotThrow(() -> {
            List<Airport> airports = flightRefreshWorkflow.getAvailableAirports("any.json").getAirports();
            assertNotNull(airports);
            assertFalse(airports.isEmpty());
            assertEquals("VIE", airports.get(0).getAirportCode());
            assertFalse(airports.get(0).getConnections().isEmpty());
            assertEquals("TSF", airports.get(0).getConnections().get(0));
        }, "Should not have thrown any exceptions.");
    }

    @Test
    void testRefreshFlights() throws IOException, InterruptedException {
        when(mockS3Client.getStringObjectFromS3(anyString())).thenReturn(AIRPORT_JSON_STRING);
        when(mockRyanairClient.getFlights(anyString(), anyString(), any(LocalDate.class))).thenReturn(new FlightList(new ArrayList<>(), "eur"));
        when(mockWizzairClient.getFlights(anyString(), anyString(), any(LocalDate.class))).thenReturn(new FlightList(new ArrayList<>(), "eur"));
        when(mockDynamoDbClient.table(anyString(), any(TableSchema.class))).thenReturn(mockFlightsTable);

        flightRefreshWorkflow.refreshFlights();

        verify(mockRyanairClient, times(FlightRefreshWorkflow.MAX_RYANAIR_REQUESTS_PER_LAMBDA)).getFlights(anyString(), anyString(), any(LocalDate.class));
    }

    @Test
    void pickRandomDay_SuccessfulPick_InRange() {
        assert(flightRefreshWorkflow.pickNumberWithWeightedProbability(0, 365) <= 365);
        assert(flightRefreshWorkflow.pickNumberWithWeightedProbability(0, 0) == 0);

        int lessThan180 = 0;
        int moreThan180 = 0;

        // testing that the first 180 items are more probable to occur than the next 185
        // probability of this happening should be so high that the test basically never fails
        for(int i = 0; i < 100; i++) {
            if(flightRefreshWorkflow.pickNumberWithWeightedProbability(0, 365) < 180)
                lessThan180++;
            else
                moreThan180++;
        }

        assert(lessThan180 > moreThan180);
    }

}
