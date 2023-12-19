package com.aerotrack.lambda;

import com.aerotrack.model.entities.Flight;
import com.aerotrack.model.entities.Trip;
import com.aerotrack.model.protocol.ScanQueryRequest;
import com.aerotrack.lambda.workflow.QueryLambdaWorkflow;
import com.aerotrack.model.protocol.ScanQueryResponse;
import com.aerotrack.utils.clients.dynamodb.AerotrackDynamoDbClient;
import com.aerotrack.utils.clients.s3.AerotrackS3Client;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class QueryLambdaTest {

    @Mock
    private AerotrackDynamoDbClient mockDynamoDbClient;
    @Mock
    private AerotrackS3Client mockS3Client;
    private QueryLambdaWorkflow queryLambdaWorkflow;
    private final LocalDate today = LocalDate.now();
    private final LocalDate tenDaysLater = today.plusDays(10);

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final String startDateString = today.format(formatter);
    private final String endDateString = tenDaysLater.format(formatter);
    private final static String AIRPORT_JSON_STRING = """
            {
              "airports":
                [
                  {
                    "airportCode" : "VIE",
                    "name" : "Vienna",
                    "countryCode" : "AT",
                    "connections" : ["TSF"]
                  },
                  {
                    "airportCode" : "TSF",
                    "name" : "Venice (Treviso)",
                    "countryCode" : "IT",
                    "connections" : ["VIE"]
                  }

                ]
            }
            """;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        when(mockS3Client.getStringObjectFromS3(any())).thenReturn(AIRPORT_JSON_STRING);
        queryLambdaWorkflow = new QueryLambdaWorkflow(mockDynamoDbClient, mockS3Client);
    }

    private static byte[] getUtf8Bytes(String input) {
        return input.getBytes(StandardCharsets.UTF_8);
    }

    private List<Flight> getGenericFirstFlights() {
        return List.of(
                new Flight("TSF", "VIE", "2021-01-08T04:58:02.000", "2021-01-08T05:58:02.000", "RY123",115),
                new Flight("TSF", "VIE", "2021-01-01T06:20:43.000", "2021-01-01T07:20:43.000", "RY123", 125),
                new Flight("TSF", "VIE", "2021-01-03T09:48:17.000", "2021-01-03T10:48:17.000", "RY123",112));
    }

    private List<Flight> getGenericSecondFlights() {
        return List.of(
                new Flight("VIE", "TSF", "2021-01-03T16:30:41.000", "2021-01-03T17:30:41.000", "RY123",77),
                new Flight("VIE", "TSF", "2021-01-06T10:16:07.000", "2021-01-06T11:16:07.000", "RY123",89),
                new Flight("VIE", "TSF", "2021-01-09T18:46:33.000", "2021-01-09T19:46:33.000", "RY123",73));
    }

    @Test
    void queryAndProcessFlights_SuccessfulQuery_CorrectProcessing() throws IOException {
        when(mockDynamoDbClient.scanFlightsBetweenDates(any(), any(), any(), any()))
                .thenReturn(getGenericFirstFlights())
                .thenReturn(getGenericSecondFlights());

        ScanQueryResponse results = queryLambdaWorkflow.queryAndProcessFlights(2, 6, startDateString, endDateString,
                List.of("TSF"), List.of("VIE"), 0, Optional.empty(),
                Optional.empty(), true);

        List<Trip> pairs = results.getTrips();

        assertFalse(pairs.isEmpty());
        assertEquals(4, pairs.size());
    }

    @Test
    void queryAndProcessFlights_NoMatchingFlights_ReturnsEmptyList() throws IOException {

        when(mockDynamoDbClient.scanFlightsBetweenDates(any(), any(), any(), any())).thenReturn(Collections.emptyList());

        ScanQueryRequest request = ScanQueryRequest.builder()
                .minDays(2)
                .maxDays(6)
                .availabilityStart(startDateString)
                .availabilityEnd(endDateString)
                .returnToSameAirport(true)
                .departureAirports(List.of("TSF"))
                .destinationAirports(List.of("VIE"))
                .build();

        ScanQueryResponse results = queryLambdaWorkflow.queryAndProcessFlights(2, 6, startDateString, endDateString,
                List.of("TSF"), List.of("VIE"), 0, Optional.empty(),
                Optional.empty(), true);
        List<Trip> pairs = results.getTrips();

        assertTrue(pairs.isEmpty());
    }

    @Test
    void queryAndProcessFlights_HighPriceVariations_CorrectSorting() throws IOException {
        when(mockDynamoDbClient.scanFlightsBetweenDates(any(), any(), any(), any()))
                .thenReturn(getGenericFirstFlights())
                .thenReturn(getGenericSecondFlights());

        ScanQueryRequest request = ScanQueryRequest.builder()
                .minDays(2)
                .maxDays(6)
                .availabilityStart(startDateString)
                .availabilityEnd(endDateString)
                .returnToSameAirport(true)
                .departureAirports(List.of("TSF"))
                .destinationAirports(List.of("VIE"))
                .build();

        ScanQueryResponse results = queryLambdaWorkflow.queryAndProcessFlights(2, 6, startDateString, endDateString,
                List.of("TSF"), List.of("VIE"), 0, Optional.empty(),
                Optional.empty(), true);
        List<Trip> pairs = results.getTrips();

        assertFalse(pairs.isEmpty());

        List<Integer> prices = pairs.stream()
                .map(Trip::getTotalPrice)
                .toList();

        // Check if each price is less than or equal to the next price in the list
        for (int i = 0; i < prices.size() - 1; i++) {
            assertTrue(prices.get(i) <= prices.get(i + 1));
        }
    }

    @Test
    void queryAndProcessFlights_OneDayTrip() throws IOException {
        when(mockDynamoDbClient.scanFlightsBetweenDates(any(), any(), any(), any()))
                .thenReturn(getGenericFirstFlights())
                .thenReturn(getGenericSecondFlights());

        ScanQueryRequest request = ScanQueryRequest.builder()
                .minDays(0)
                .maxDays(0)
                .availabilityStart(startDateString)
                .availabilityEnd(endDateString)
                .returnToSameAirport(true)
                .departureAirports(List.of("TSF"))
                .destinationAirports(List.of("VIE"))
                .build();

        ScanQueryResponse results = queryLambdaWorkflow.queryAndProcessFlights(0, 0, startDateString, endDateString,
                List.of("TSF"), List.of("VIE"), 0, Optional.empty(),
                Optional.empty(), true);
        List<Trip> pairs = results.getTrips();

        assertEquals(pairs.size(), 1);

        List<Flight> outboundFlights = pairs.get(0).getOutboundFlights();
        List<Flight> returnFlights = pairs.get(0).getReturnFlights();

        assertEquals(outboundFlights.get(0).getDepartureDateTime(), "2021-01-03T09:48:17.000");
        assertEquals(returnFlights.get(0).getDepartureDateTime(), "2021-01-03T16:30:41.000");

    }

}
