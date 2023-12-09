package com.aerotrack.lambda;

import com.aerotrack.model.Flight;
import com.aerotrack.model.FlightPair;
import com.aerotrack.model.ScanQueryRequest;
import com.aerotrack.lambda.workflow.QueryLambdaWorkflow;
import com.aerotrack.model.ScanQueryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class QueryLambdaTest {

    @Mock
    private DynamoDbEnhancedClient mockDynamoDbEnhancedClient;
    @Mock
    private S3Client mockS3Client;
    @Mock
    private DynamoDbTable<Flight> mockFlightTable;
    @Mock
    private PageIterable<Flight> mockPageIterable;
    @Mock
    private SdkIterable<Flight> mockSdkIterable;
    @Mock
    private ResponseInputStream<GetObjectResponse> mockResponseInputStream;
    private QueryLambdaWorkflow queryLambdaWorkflow;
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

        when(mockFlightTable.query(any(QueryConditional.class))).thenReturn(mockPageIterable);
        when(mockPageIterable.items()).thenReturn(mockSdkIterable);
        when(mockDynamoDbEnhancedClient.table(any(), any()))
                .thenAnswer((Answer<DynamoDbTable<Flight>>) invocation -> mockFlightTable);
        when(mockS3Client.getObject((GetObjectRequest) any())).thenReturn(mockResponseInputStream);

        when(mockResponseInputStream.readAllBytes()).thenReturn(getUtf8Bytes(AIRPORT_JSON_STRING));

        queryLambdaWorkflow = new QueryLambdaWorkflow(mockDynamoDbEnhancedClient, mockS3Client);
    }

    private static byte[] getUtf8Bytes(String input) {
        return input.getBytes(StandardCharsets.UTF_8);
    }

    private List<Flight> getGenericFirstFlights() {
        return List.of(
                new Flight("TSF", "VIE", "2021-01-01T06:20:43.000", "2021-01-01T07:20:43.000", "RY123", 125),
                new Flight("TSF", "VIE", "2021-01-03T09:48:17.000", "2021-01-03T10:48:17.000", "RY123",112),
                new Flight("TSF", "VIE", "2021-01-08T04:58:02.000", "2021-01-08T05:58:02.000", "RY123",115));
    }

    private List<Flight> getGenericSecondFlights() {
        return List.of(
                new Flight("VIE", "TSF", "2021-01-03T16:30:41.000", "2021-01-03T17:30:41.000", "RY123",77),
                new Flight("VIE", "TSF", "2021-01-09T18:46:33.000", "2021-01-09T19:46:33.000", "RY123",73),
                new Flight("VIE", "TSF", "2021-01-06T10:16:07.000", "2021-01-06T11:16:07.000", "RY123",89));
    }

    @Test
    void queryAndProcessFlights_SuccessfulQuery_CorrectProcessing() {
        when(mockSdkIterable.stream())
                .thenAnswer(inv -> getGenericFirstFlights().stream())
                .thenAnswer(inv -> getGenericSecondFlights().stream());

        ScanQueryRequest request = ScanQueryRequest.builder()
                .minDays(2)
                .maxDays(6)
                .availabilityStart("20210101")
                .availabilityEnd("20210110")
                .returnToSameAirport(true)
                .departureAirports(List.of("TSF"))
                .destinationAirports(List.of("VIE"))
                .build();

        ScanQueryResponse results = queryLambdaWorkflow.queryAndProcessFlights(request);
        List<FlightPair> pairs = results.getFlightPairs();

        assertFalse(pairs.isEmpty());
        assertEquals(3, pairs.size());
    }

    @Test
    void queryAndProcessFlights_NoMatchingFlights_ReturnsEmptyList() {

        when(mockSdkIterable.stream()).thenAnswer(inv -> Stream.of());

        ScanQueryRequest request = ScanQueryRequest.builder()
                .minDays(2)
                .maxDays(6)
                .availabilityStart("20220101")
                .availabilityEnd("20220107")
                .returnToSameAirport(true)
                .departureAirports(List.of("TSF"))
                .destinationAirports(List.of("VIE"))
                .build();

        ScanQueryResponse results = queryLambdaWorkflow.queryAndProcessFlights(request);
        List<FlightPair> pairs = results.getFlightPairs();

        assertTrue(pairs.isEmpty());
    }

    @Test
    void queryAndProcessFlights_HighPriceVariations_CorrectSorting() {
        when(mockSdkIterable.stream())
                .thenAnswer(inv -> getGenericFirstFlights().stream())
                .thenAnswer(inv -> getGenericSecondFlights().stream());

        ScanQueryRequest request = ScanQueryRequest.builder()
                .minDays(2)
                .maxDays(6)
                .availabilityStart("20210101")
                .availabilityEnd("20210107")
                .returnToSameAirport(true)
                .departureAirports(List.of("TSF"))
                .destinationAirports(List.of("VIE"))
                .build();

        ScanQueryResponse results = queryLambdaWorkflow.queryAndProcessFlights(request);
        List<FlightPair> pairs = results.getFlightPairs();

        assertFalse(pairs.isEmpty());

        List<Integer> prices = pairs.stream()
                .map(FlightPair::getTotalPrice)
                .toList();

        // Check if each price is less than or equal to the next price in the list
        for (int i = 0; i < prices.size() - 1; i++) {
            assertTrue(prices.get(i) <= prices.get(i + 1));
        }
    }

    @Test
    void queryAndProcessFlights_OneDayTrip() {
        when(mockSdkIterable.stream())
                .thenAnswer(inv -> getGenericFirstFlights().stream())
                .thenAnswer(inv -> getGenericSecondFlights().stream());

        ScanQueryRequest request = ScanQueryRequest.builder()
                .minDays(0)
                .maxDays(0)
                .availabilityStart("2021-01-01")
                .availabilityEnd("2021-01-07")
                .returnToSameAirport(true)
                .departureAirports(List.of("TSF"))
                .destinationAirports(List.of("VIE"))
                .build();

        ScanQueryResponse results = queryLambdaWorkflow.queryAndProcessFlights(request);
        List<FlightPair> pairs = results.getFlightPairs();

        assertEquals(pairs.size(), 1);

        Flight outboundFlight = pairs.get(0).getOutboundFlight();
        Flight returnFlight = pairs.get(0).getReturnFlight();

        assertEquals(outboundFlight.getDepartureDateTime(), "2021-01-03T09:48:17.000");
        assertEquals(returnFlight.getDepartureDateTime(), "2021-01-03T16:30:41.000");

    }

}
