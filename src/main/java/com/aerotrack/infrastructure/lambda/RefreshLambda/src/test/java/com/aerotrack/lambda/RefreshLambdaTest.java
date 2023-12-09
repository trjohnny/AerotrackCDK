package com.aerotrack.lambda;

import com.aerotrack.lambda.workflow.RefreshWorkflow;
import com.aerotrack.model.entities.Airport;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class RefreshLambdaTest {

    @Mock
    private AerotrackS3Client mockS3Client;
    @Mock
    private ResponseInputStream<GetObjectResponse> mockResponseInputStream;

    private RefreshWorkflow workflow;

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
        workflow = new RefreshWorkflow(mockS3Client, null);

    }

    private static byte[] getUtf8Bytes(String input) {
        return input.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void getAvailableAirports_SuccessfulRequest_CorrectMapping() {

        assertDoesNotThrow(() -> {
            List<Airport> airports = workflow.getAvailableAirports().getAirports();
            assertNotNull(airports);
            assertFalse(airports.isEmpty());
            assertEquals("VIE", airports.get(0).getAirportCode());
            assertFalse(airports.get(0).getConnections().isEmpty());
            assertEquals("TSF", airports.get(0).getConnections().get(0));
        }, "Should not have thrown any exceptions.");
    }

    @Test
    void pickRandomDay_SuccessfulPick_InRange() {
        assert(workflow.pickNumberWithWeightedProbability(0, 365) <= 365);
        assert(workflow.pickNumberWithWeightedProbability(0, 0) == 0);

        int lessThan180 = 0;
        int moreThan180 = 0;

        // testing that the first 180 items are more probable to occur than the next 185
        // probability of this happening should be so high that the test basically never fails
        for(int i = 0; i < 100; i++) {
            if(workflow.pickNumberWithWeightedProbability(0, 365) < 180)
                lessThan180++;
            else
                moreThan180++;
        }

        assert(lessThan180 > moreThan180);
    }

}
