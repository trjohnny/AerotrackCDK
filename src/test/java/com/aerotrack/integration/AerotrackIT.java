package com.aerotrack.integration;

import com.aerotrack.model.entities.Trip;
import com.aerotrack.model.protocol.ScanQueryRequest;
import com.aerotrack.utils.clients.apigateway.AerotrackApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class AerotrackIT {

    private AerotrackApiClient aerotrackApiClient;
    private final LocalDate today = LocalDate.now();
    private final LocalDate tenDaysLater = today.plusDays(10);
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final String startDateString = today.format(formatter);
    private final String endDateString = tenDaysLater.format(formatter);

    @BeforeEach
    public void setUp() {
        // Initialize your API client
        aerotrackApiClient = AerotrackApiClient.create();
    }

    @Test
    public void testGetBestFlight() {
        // Create a ScanQueryRequest with the required parameters for your test
        ScanQueryRequest scanQueryRequest = ScanQueryRequest.builder()
                .minDays(2)
                .maxDays(6) // < 10
                .availabilityStart(startDateString)
                .availabilityEnd(endDateString)
                .returnToSameAirport(true)
                .departureAirports(List.of("TSF", "VCE"))
                .destinationAirports(List.of("VIE", "ARN", "MAD"))
                .build();
        // Populate scanQueryRequest with test data

        // Execute the API call
        List<Trip> flightPairs = aerotrackApiClient.getBestFlight(scanQueryRequest);

        // Assertions to verify the response
        assertNotNull(flightPairs, "Flight pairs should not be null");
        assertFalse(flightPairs.isEmpty());

        // Add more assertions as needed based on your expected response
        // For example, you can check the size of the list, contents of the flight pairs, etc.
    }
}
