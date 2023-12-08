package com.aerotrack.lambda.workflow;

import com.aerotrack.model.Flight;
import com.aerotrack.model.FlightPair;
import com.aerotrack.model.ScanQueryRequest;
import com.aerotrack.model.ScanQueryResponse;
import com.aerotrack.utils.S3Utils;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


@Slf4j
public class QueryLambdaWorkflow {
    private static final String AIRPORTS_OBJECT_NAME = "airports.json";
    private final DynamoDbTable<Flight> flightTable;
    static final TableSchema<Flight> FLIGHT_TABLE_SCHEMA = TableSchema.fromClass(Flight.class);
    static final String FLIGHT_TABLE_ENV_VAR = "FLIGHT_TABLE";
    static final String AIRPORTS_BUCKET_ENV_VAR = "AIRPORTS_BUCKET";
    private final S3Client s3Client;


    public QueryLambdaWorkflow(DynamoDbEnhancedClient dynamoDbEnhancedClient, S3Client s3Client) {
        this.flightTable = dynamoDbEnhancedClient.table(
                System.getenv(FLIGHT_TABLE_ENV_VAR),
                FLIGHT_TABLE_SCHEMA);
        this.s3Client = s3Client;
    }

    public ScanQueryResponse queryAndProcessFlights(ScanQueryRequest request) {
        JSONObject airportsJson;

        try {
            airportsJson = S3Utils.getJsonObjectFromS3(s3Client, System.getenv(AIRPORTS_BUCKET_ENV_VAR), AIRPORTS_OBJECT_NAME);
        } catch (IOException ioException) {
            // No need to handle or propagate the IOException, we are not able to handle it.
            // The QueryRequestHandler will log the error
            throw new RuntimeException(ioException);
        }

        JSONArray airportsArray = airportsJson.getJSONArray("airports");

        // This map is used to check the existence of every airport in the request and their possible connections.
        // In this way we limit the number of calls to DynamoDB
        Map<String, Set<String>> airportsConnections = getAirportConnectionsMap(airportsArray);

        List<FlightPair> flightPairs = new ArrayList<>();
        Map<String, List<Flight>> allReturnFlights = new HashMap<>();

        // Pre-fetch return flights if not returning to the same airport
        if (!request.getReturnToSameAirport()) {
            for (String destination : request.getDestinationAirports()) {
                if (! airportsConnections.containsKey(destination))
                    continue;

                List<Flight> returnFlightsForDestination = new ArrayList<>();
                for (String returnDeparture : request.getDepartureAirports()) {
                    if (! airportsConnections.get(destination).contains(returnDeparture))
                        continue;

                    returnFlightsForDestination.addAll(scanFlights(destination, returnDeparture,
                            request.getAvailabilityStart(), request.getAvailabilityEnd()));
                }
                allReturnFlights.put(destination, returnFlightsForDestination);
            }
        }

        // Process outbound and return flights
        for (String departure : request.getDepartureAirports()) {
            if (! airportsConnections.containsKey(departure))
                continue;

            for (String destination : request.getDestinationAirports()) {
                if (! airportsConnections.get(departure).contains(destination))
                    continue;

                List<Flight> outboundFlights = scanFlights(departure, destination,
                        request.getAvailabilityStart(), request.getAvailabilityEnd());

                log.debug(outboundFlights.toString());

                List<Flight> returnFlights = request.getReturnToSameAirport() ?
                        scanFlights(destination, departure, request.getAvailabilityStart(), request.getAvailabilityEnd()) :
                        allReturnFlights.getOrDefault(destination, new ArrayList<>());

                log.debug(returnFlights.toString());

                // Find matching pairs
                for (Flight outboundFlight : outboundFlights) {
                    for (Flight returnFlight : returnFlights) {
                        int duration = calculateDuration(outboundFlight, returnFlight);
                        if (duration < request.getMinDays()) continue;
                        if (duration > request.getMaxDays()) break;

                        int totalPrice = (int) (outboundFlight.getPrice() + returnFlight.getPrice());
                        flightPairs.add(new FlightPair(outboundFlight, returnFlight, totalPrice));
                    }
                }
            }
        }

        List<FlightPair> sortedPairs = flightPairs.stream()
                .sorted(Comparator.comparing(FlightPair::getTotalPrice))
                .limit(10)
                .toList();

        return ScanQueryResponse.builder()
                .flightPairs(sortedPairs)
                .build();
    }

    private Map<String, Set<String>> getAirportConnectionsMap(JSONArray airportsArray) {
        Map<String, Set<String>> airportsConnections = new HashMap<>();

        for (int i = 0; i < airportsArray.length(); i++) {
            JSONObject airport = airportsArray.getJSONObject(i);
            String airportCode = airport.getString("airportCode");
            JSONArray connectionsArray = airport.getJSONArray("connections");
            Set<String> connections = new HashSet<>();
            for (int j = 0; j < connectionsArray.length(); j++) {
                connections.add(connectionsArray.getString(j));
            }
            airportsConnections.put(airportCode, connections);
        }

        return airportsConnections;
    }


    private List<Flight> scanFlights(String departure, String destination, String availabilityStart, String availabilityEnd) {
        String partitionKey = departure + "-" + destination;
        Key startKey = Key.builder().partitionValue(partitionKey).sortValue(availabilityStart).build();
        Key endKey = Key.builder().partitionValue(partitionKey).sortValue(availabilityEnd).build();

        log.info("Querying DynamoDb on table: {}", flightTable.tableName());
        return flightTable.query(QueryConditional.sortBetween(startKey, endKey))
                .items()
                .stream()
                .toList();
    }

    private int calculateDuration(Flight outboundFlight, Flight returnFlight) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;

        LocalDateTime departureDateTime = LocalDateTime.parse(outboundFlight.getDepartureDateTime(), formatter);
        LocalDateTime returnDateTime = LocalDateTime.parse(returnFlight.getDepartureDateTime(), formatter);

        Duration duration = Duration.between(departureDateTime, returnDateTime);
        return (int) duration.toDays();
    }
}
