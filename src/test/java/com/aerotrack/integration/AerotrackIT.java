package com.aerotrack.integration;

import com.aerotrack.common.InfraUtils;
import com.aerotrack.model.entities.AerotrackStage;
import com.aerotrack.model.entities.Flight;
import com.aerotrack.model.entities.Trip;
import com.aerotrack.model.protocol.ScanQueryRequest;
import com.aerotrack.utils.clients.api.AerotrackApiClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.Credentials;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class AerotrackIT {

    private AerotrackApiClient aerotrackApiClient;
    private final String startDateString = LocalDate.now().plusYears(1).minusDays(6).atStartOfDay().format(requestFormatter);
    private final String endDateString = LocalDate.now().plusYears(1).minusDays(1).atStartOfDay().format(requestFormatter);
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    private static final DateTimeFormatter requestFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static DynamoDbTable<Flight> flightsTable;
    private static Flight flight1, flight2;

    @BeforeAll
    public static void setUpClass() {
        // Assume the IAM role using STS
        AssumeRoleResponse assumeRoleResponse;
        try (StsClient stsClient = StsClient.builder().region(Region.AWS_GLOBAL).build()) {
            assumeRoleResponse = stsClient.assumeRole(AssumeRoleRequest.builder()
                    .roleArn("arn:aws:iam::073873382417:role/QueryLambdaTestRole")
                    .roleSessionName("DynamoDbTestSession")
                    .build());
        }
        Credentials tempCredentials = assumeRoleResponse.credentials();

        // Create session credentials for DynamoDB access
        AwsSessionCredentials sessionCredentials = AwsSessionCredentials.create(
                tempCredentials.accessKeyId(),
                tempCredentials.secretAccessKey(),
                tempCredentials.sessionToken());

        // Initialize DynamoDB Enhanced Client with the assumed role credentials
        DynamoDbClient dynamoDbClient = DynamoDbClient.builder()
                .credentialsProvider(StaticCredentialsProvider.create(sessionCredentials))
                .region(Region.of("eu-west-1"))
                .build();

        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();

        flightsTable = enhancedClient.table(System.getenv("FLIGHTS_TABLE"), TableSchema.fromBean(Flight.class));
        // Insert test data
        LocalDateTime departureTime1 = LocalDateTime.now().plusYears(1).minusDays(5);
        LocalDateTime departureTime2 = departureTime1.plusDays(2);

        flight1 = new Flight("VCE", "DUB", departureTime1.format(formatter), departureTime1.plusHours(2).format(formatter), "FR830", 100.0);
        flight2 = new Flight("DUB", "VCE", departureTime2.format(formatter), departureTime2.plusHours(2).format(formatter), "FR831", 150.0);

        flightsTable.putItem(flight1);
        flightsTable.putItem(flight2);
    }

    @BeforeEach
    public void setUp() {
        // Initialize your API client
        aerotrackApiClient = AerotrackApiClient.create(getStage());
    }

    private AerotrackStage getStage() {
        if (InfraUtils.isPersonalDeployment())
            return AerotrackStage.fromName(System.getenv("AEROTRACK_DEV"));
        return AerotrackStage.ALPHA;
    }

    @Test
    public void testGetBestFlight() {
        // Create a ScanQueryRequest with the required parameters for your test

        ScanQueryRequest scanQueryRequest = ScanQueryRequest.builder()
                .minDays(1)
                .maxDays(5)
                .availabilityStart(startDateString)
                .availabilityEnd(endDateString)
                .returnToSameAirport(true)
                .departureAirports(List.of("VCE"))
                .destinationAirports(List.of("DUB"))
                .build();
        // Populate scanQueryRequest with test data

        // Execute the API call
        List<Trip> trips = aerotrackApiClient.getBestFlight(scanQueryRequest);

        // Assertions to verify the response
        assertNotNull(trips, "Flight pairs should not be null");
        assertFalse(trips.isEmpty());

        boolean present = false;

        for (Trip trip : trips) {
            if (trip.getOutboundFlights().get(0).equals(flight1) && trip.getReturnFlights().get(0).equals(flight2))
                present = true;
        }

        assertTrue(present);
    }

    @AfterAll
    public static void tearDownClass() {

        flightsTable.deleteItem(Key.builder().partitionValue(flight1.getDirection()).sortValue(flight1.getDepartureDateTime()).build());
        flightsTable.deleteItem(Key.builder().partitionValue(flight2.getDirection()).sortValue(flight2.getDepartureDateTime()).build());
    }
}
