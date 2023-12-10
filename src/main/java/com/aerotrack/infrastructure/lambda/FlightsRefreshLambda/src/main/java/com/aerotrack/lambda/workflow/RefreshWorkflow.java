package com.aerotrack.lambda.workflow;

import com.aerotrack.model.entities.Flight;
import com.aerotrack.model.entities.Airport;
import com.aerotrack.model.entities.AirportsJsonFile;
import com.aerotrack.utils.Constants;
import com.aerotrack.utils.clients.ryanair.RyanairClient;
import com.aerotrack.utils.clients.s3.AerotrackS3Client;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import retrofit2.HttpException;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;
import software.amazon.awssdk.utils.Pair;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;

import static com.aerotrack.utils.Constants.AIRPORTS_OBJECT_NAME;


@Slf4j
public class RefreshWorkflow  {

    private final DynamoDbEnhancedClient dynamoDbEnhancedClient;
    private final AerotrackS3Client s3Client;
    private final RyanairClient ryanairClient;
    private final DynamoDbTable<Flight> flightsTable;

    private static final int DAY_PICK_WEIGHT_FACTOR = 30;
    public static final int MAX_REQUESTS_PER_LAMBDA = 900;

    public RefreshWorkflow(AerotrackS3Client s3Client, DynamoDbEnhancedClient dynamoDbEnhancedClient, RyanairClient ryanairClient) {
        this.s3Client = s3Client;
        this.dynamoDbEnhancedClient = dynamoDbEnhancedClient;
        this.ryanairClient = ryanairClient;

        this.flightsTable = dynamoDbEnhancedClient.table(System.getenv(Constants.FLIGHT_TABLE_ENV_VAR), TableSchema.fromBean(Flight.class));
    }
    public void refreshFlights() throws IOException, InterruptedException {

        AirportsJsonFile airportList = getAvailableAirports();
        Pair<String, String> randomConnection;
        LocalDate randomDate;
        List<Flight> flights;

        for(int i = 0; i < MAX_REQUESTS_PER_LAMBDA; i++) {

            try {
                randomConnection = getRandomAirportPair(airportList);
                randomDate = LocalDate.now().plusDays(pickNumberWithWeightedProbability(0, 365));
                flights = ryanairClient.getFlights(randomConnection.left(), randomConnection.right(), randomDate);
                writeFlightsToTable(flights);
                log.info("Success: {}", i);
            } catch (HttpException httpException) {
                log.error("HttpException caught: {}", httpException.message());
                if (httpException.code() == 400) return;
            } catch (RuntimeException e) {
                log.error("A RuntimeException exception occurred for the single request: " + e);
            }
        }
    }

    public AirportsJsonFile getAvailableAirports() throws IOException {

        String airportsJson = s3Client.getStringObjectFromS3(AIRPORTS_OBJECT_NAME);
        return new ObjectMapper().readValue(airportsJson, new TypeReference<>() {});
    }

    public void writeFlightsToTable(List<Flight> flights) {
        if(flights.isEmpty())
            return;

        WriteBatch.Builder<Flight> builder = WriteBatch.builder(Flight.class).mappedTableResource(flightsTable);

        for (Flight flight : flights) {
            builder.addPutItem(flight);
        }

        BatchWriteItemEnhancedRequest batchWriteItemEnhancedRequest = BatchWriteItemEnhancedRequest.builder()
                .writeBatches(builder.build())
                .build();

        dynamoDbEnhancedClient.batchWriteItem(batchWriteItemEnhancedRequest);
    }

    public int pickNumberWithWeightedProbability(int min, int max) {
        Random random = new Random();
        double totalWeight = 0.0;

        // Calculate total weight for normalization
        for (int i = min; i <= max; i++) {
            totalWeight += calculateDayPickWeight(i);
        }

        double randWeight = random.nextDouble() * totalWeight;
        double currentWeight = 0.0;

        // Pick a number based on its weight
        for (int i = min; i <= max; i++) {
            currentWeight += calculateDayPickWeight(i);
            if (randWeight <= currentWeight) {
                return i;
            }
        }

        // This line should not be reached under normal circumstances
        return min;
    }

    public Pair<String, String> getRandomAirportPair(AirportsJsonFile airportList) {

        int totalConnections = airportList.getAirports().stream()
                .mapToInt(airport -> airport.getConnections().size())
                .sum();

        Random random = new Random();
        int randomConnectionIndex = random.nextInt(totalConnections);
        int connectionSum = 0;

        for (Airport airport : airportList.getAirports()) {
            int connections = airport.getConnections().size();
            if (randomConnectionIndex < connectionSum + connections) {
                int randomToIndex = randomConnectionIndex - connectionSum;
                String toAirportCode = airport.getConnections().get(randomToIndex);
                return Pair.of(airport.getAirportCode(), toAirportCode);
            }
            connectionSum += connections;
        }

        throw new IllegalArgumentException("Could not get a random airport pair");
    }

    private double calculateDayPickWeight(int delayDays) {
        return 1.0 / (1 + (DAY_PICK_WEIGHT_FACTOR * (1.0 / 365) * delayDays));
    }
}


