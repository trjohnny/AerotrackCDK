package com.aerotrack.lambda.workflow;

import com.aerotrack.model.entities.Flight;
import com.aerotrack.model.entities.Airport;
import com.aerotrack.model.entities.AirportsJsonFile;
import com.aerotrack.model.entities.FlightList;
import com.aerotrack.utils.Constants;
import com.aerotrack.utils.clients.api.currencyConverter.CurrencyConverter;
import com.aerotrack.utils.clients.api.ryanair.RyanairClient;
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
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.utils.Pair;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.aerotrack.utils.Constants.AIRPORTS_OBJECT_NAME;


@Slf4j
public class FlightRefreshWorkflow {

    private final DynamoDbEnhancedClient dynamoDbEnhancedClient;
    private final AerotrackS3Client s3Client;
    private final RyanairClient ryanairClient;
    private final DynamoDbTable<Flight> flightsTable;
    private final CurrencyConverter currencyConverter;

    private static final int DAY_PICK_WEIGHT_FACTOR = 30;
    public static final int MAX_REQUESTS_PER_LAMBDA = 900;

    public FlightRefreshWorkflow(AerotrackS3Client s3Client, DynamoDbEnhancedClient dynamoDbEnhancedClient, RyanairClient ryanairClient, CurrencyConverter currencyConverter) {
        this.s3Client = s3Client;
        this.dynamoDbEnhancedClient = dynamoDbEnhancedClient;
        this.ryanairClient = ryanairClient;
        this.currencyConverter = currencyConverter;
        this.flightsTable = dynamoDbEnhancedClient.table(System.getenv(Constants.FLIGHT_TABLE_ENV_VAR), TableSchema.fromBean(Flight.class));
    }
    public void refreshFlights() throws IOException, InterruptedException {

        AirportsJsonFile airportList = getAvailableAirports();
        Pair<String, String> randomConnection;
        LocalDate randomDate;

        List<FlightList> flightAndPrice = new ArrayList<>();
        List<Flight> flights = new ArrayList<>();
        Map<String, Double> conversionRate = new HashMap<>();

        int totalSuccess = 0;
        for (int i = 0; i < MAX_REQUESTS_PER_LAMBDA; i++) {

            try {
                randomConnection = getRandomAirportPair(airportList);
                randomDate = LocalDate.now().plusDays(pickNumberWithWeightedProbability(0, 365));
                FlightList flightList = ryanairClient.getFlights(randomConnection.left(), randomConnection.right(), randomDate);
                flightAndPrice.add(flightList);
                conversionRate.put(flightList.getCurrency(), null);
                totalSuccess++;
                log.info("Success: {}", i);
            } catch (HttpException httpException) {
                log.error("HttpException caught: {}", httpException.message());
                if (httpException.code() == 400) break;
            } catch (RuntimeException e) {
                log.error("A RuntimeException exception occurred for the single request: " + e);
            }
        }

        conversionRate.replaceAll((c, v) -> currencyConverter.getConversionFactor(c, "eur"));

        for(FlightList flightList : flightAndPrice) {
            for(Flight flight : flightList.getFlights()) {
                flight.setPrice(flight.getPrice()*conversionRate.get(flightList.getCurrency()));
            }
            writeFlightsToTable(flightList.getFlights()); // there's a limit to the batch size
        }


        PutMetricDataRequest request = PutMetricDataRequest.builder()
                .namespace(Constants.METRIC_REFRESH_FLIGHTS_NAMESPACE)
                .metricData(
                        List.of(MetricDatum.builder()
                        .metricName(Constants.METRIC_REFRESH_FLIGHTS_API_CALLS)
                        .value(totalSuccess+0.0)
                        .timestamp(Instant.now())
                        .build()))
                .build();

        try (CloudWatchClient cw = CloudWatchClient.create()) {
            cw.putMetricData(request);
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


