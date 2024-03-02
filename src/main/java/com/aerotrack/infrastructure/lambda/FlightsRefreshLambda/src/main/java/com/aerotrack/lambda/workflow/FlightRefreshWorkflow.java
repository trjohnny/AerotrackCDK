package com.aerotrack.lambda.workflow;

import com.aerotrack.model.entities.Airline;
import com.aerotrack.model.entities.Flight;
import com.aerotrack.model.entities.Airport;
import com.aerotrack.model.entities.AirportsJsonFile;
import com.aerotrack.model.entities.FlightList;
import com.aerotrack.model.exceptions.DirectionNotAvailableException;
import com.aerotrack.common.Constants;
import com.aerotrack.utils.clients.api.AirlineApiClient;
import com.aerotrack.utils.clients.api.CurrencyConverterApiClient;
import com.aerotrack.utils.clients.api.RyanairApiClient;
import com.aerotrack.utils.clients.api.WizzairApiClient;
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

@Slf4j
public class FlightRefreshWorkflow {

    private static final String RYANAIR_API_CALLS = "RyanairApiCalls";
    private static final String WIZZAIR_API_CALLS = "WizzairApiCalls";
    private final DynamoDbEnhancedClient dynamoDbEnhancedClient;
    private final AerotrackS3Client s3Client;
    private final RyanairApiClient ryanairClient;
    private final WizzairApiClient wizzairApiClient;
    private final DynamoDbTable<Flight> flightsTable;
    private final CurrencyConverterApiClient currencyConverter;
    private static final int DAY_PICK_WEIGHT_FACTOR = 30;
    public static final int MAX_RYANAIR_REQUESTS_PER_LAMBDA = 500;
    public static final int MAX_WIZZAIR_REQUESTS_PER_LAMBDA = 200;
    public static final String RYANAIR_CONNECTIONS_OBJECT_NAME = "ryanair_airports.json";
    public static final String WIZZAIR_CONNECTIONS_OBJECT_NAME = "wizzair_airports.json";
    public static final String METRIC_REFRESH_FLIGHTS_NAMESPACE = "RefreshLambdaMetric";

    public FlightRefreshWorkflow(AerotrackS3Client s3Client, DynamoDbEnhancedClient dynamoDbEnhancedClient,
                                 RyanairApiClient ryanairClient, WizzairApiClient wizzairApiClient,
                                 CurrencyConverterApiClient currencyConverter) {
        this.s3Client = s3Client;
        this.dynamoDbEnhancedClient = dynamoDbEnhancedClient;
        this.ryanairClient = ryanairClient;
        this.wizzairApiClient = wizzairApiClient;
        this.currencyConverter = currencyConverter;
        this.flightsTable = dynamoDbEnhancedClient.table(System.getenv(Constants.FLIGHT_TABLE_ENV_VAR), TableSchema.fromBean(Flight.class));
    }

    public void refreshFlights() throws IOException, InterruptedException {
        AirportsJsonFile ryanairAirportJsonFile = getAvailableAirports(RYANAIR_CONNECTIONS_OBJECT_NAME);
        AirportsJsonFile wizzairAirportJsonFile = getAvailableAirports(WIZZAIR_CONNECTIONS_OBJECT_NAME);

        List<FlightList> flightsAndPrices = new ArrayList<>();
        Map<String, Double> conversionRate = new HashMap<>();

        List<FlightList> ryanairFlightsAndPrices = fetchFlights(ryanairAirportJsonFile, conversionRate, ryanairClient, "Ryanair");
        List<FlightList> wizzairFlightsAndPrices = fetchFlights(wizzairAirportJsonFile, conversionRate, wizzairApiClient, "Wizzair");

        recordMetric(ryanairFlightsAndPrices.size(), RYANAIR_API_CALLS);
        recordMetric(wizzairFlightsAndPrices.size(), WIZZAIR_API_CALLS);

        flightsAndPrices.addAll(ryanairFlightsAndPrices);
        flightsAndPrices.addAll(wizzairFlightsAndPrices);

        updateConversionRates(conversionRate);

        flightsAndPrices.forEach(flightList -> {
            double conversionFactor = conversionRate.getOrDefault(flightList.getCurrency(), 1.0);
            flightList.getFlights().forEach(flight -> flight.setPrice(flight.getPrice() * conversionFactor));
            writeFlightsToTable(flightList.getFlights());
        });
    }

    private List<FlightList> fetchFlights(AirportsJsonFile airportList, Map<String, Double> conversionRate, AirlineApiClient apiClient, String airline) {
        List<FlightList> flightsAndPrices = new ArrayList<>();
        int totalSuccess = 0;

        for (int i = 0; i < getMaxRequests(Airline.fromName(airline)); i++) {
            try {
                Pair<String, String> randomConnection = getRandomAirportPair(airportList);
                LocalDate randomDate = LocalDate.now().plusDays(pickNumberWithWeightedProbability(1, 365));
                FlightList flightList = apiClient.getFlights(randomConnection.left(), randomConnection.right(), randomDate);
                flightsAndPrices.add(flightList);
                conversionRate.putIfAbsent(flightList.getCurrency().toLowerCase(), null);
                totalSuccess++;
            } catch (HttpException httpException) {
                log.error("HttpException caught: {}", httpException.message());
                if (httpException.code() == 400) break;
            } catch (DirectionNotAvailableException directionNotAvailableException) {
                log.warn("DirectionNotAvailableException occurred: " + directionNotAvailableException.getMessage());
            } catch (RuntimeException e) {
                log.error("RuntimeException occurred: ", e);
            }
        }

        log.info("Total successful {} requests: {}", airline, totalSuccess);
        return flightsAndPrices;
    }

    private int getMaxRequests(Airline airline) {
        return switch (airline) {
            case RYANAIR -> MAX_RYANAIR_REQUESTS_PER_LAMBDA;
            case WIZZAIR -> MAX_WIZZAIR_REQUESTS_PER_LAMBDA;
        };
    }

    private void updateConversionRates(Map<String, Double> conversionRate) {
        conversionRate.keySet().forEach(currency ->
                conversionRate.put(currency, currencyConverter.getConversionFactor(currency, "eur")));
    }

    private void recordMetric(int totalApiCalls, String metricName) {
        PutMetricDataRequest request = PutMetricDataRequest.builder()
                .namespace(METRIC_REFRESH_FLIGHTS_NAMESPACE)
                .metricData(MetricDatum.builder()
                        .metricName(metricName)
                        .value((double) totalApiCalls)
                        .timestamp(Instant.now())
                        .build())
                .build();

        try (CloudWatchClient cw = CloudWatchClient.create()) {
            cw.putMetricData(request);
        }
    }

    public AirportsJsonFile getAvailableAirports(String connectionsFileName) throws IOException {

        String airportsJson = s3Client.getStringObjectFromS3(connectionsFileName);
        return new ObjectMapper().readValue(airportsJson, new TypeReference<>() {});
    }

    private void writeFlightsToTable(List<Flight> flights) {
        if (flights.isEmpty()) {
            return;
        }

        final int MAX_BATCH_SIZE = 25;

        // Calculate the number of batches needed
        int totalBatches = (flights.size() + MAX_BATCH_SIZE - 1) / MAX_BATCH_SIZE;

        for (int i = 0; i < totalBatches; i++) {
            // Determine the sublist for the current batch
            int start = i * MAX_BATCH_SIZE;
            int end = Math.min((i + 1) * MAX_BATCH_SIZE, flights.size());
            List<Flight> batchFlights = flights.subList(start, end);

            // Create a new builder for each batch
            WriteBatch.Builder<Flight> batchBuilder = WriteBatch.builder(Flight.class).mappedTableResource(flightsTable);

            // Add items to the batch
            for (Flight flight : batchFlights) {
                batchBuilder.addPutItem(flight);
            }

            // Build the batch write request
            BatchWriteItemEnhancedRequest batchWriteItemEnhancedRequest = BatchWriteItemEnhancedRequest.builder()
                    .writeBatches(batchBuilder.build())
                    .build();

            // Execute the batch write operation
            dynamoDbEnhancedClient.batchWriteItem(batchWriteItemEnhancedRequest);
        }
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

    private Pair<String, String> getRandomAirportPair(AirportsJsonFile airportList) {

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