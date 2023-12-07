package com.aerotrack.lambda.query.workflow;

import com.aerotrack.model.Flight;
import com.aerotrack.model.FlightPair;
import com.aerotrack.model.ScanQueryRequest;
import com.aerotrack.model.ScanQueryResponse;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class QueryLambdaWorkflow {
    private final DynamoDbTable<Flight> flightTable;
    static final TableSchema<Flight> FLIGHT_TABLE_SCHEMA = TableSchema.fromClass(Flight.class);
    static final String FLIGHT_TABLE_ENV_VAR = "FLIGHT_TABLE";


    public QueryLambdaWorkflow(DynamoDbEnhancedClient dynamoDbEnhancedClient) {
        this.flightTable = dynamoDbEnhancedClient.table(
                System.getenv(FLIGHT_TABLE_ENV_VAR),
                FLIGHT_TABLE_SCHEMA);
    }

    public ScanQueryResponse queryAndProcessFlights(ScanQueryRequest request) {
        List<FlightPair> flightPairs = new ArrayList<>();
        Map<String, List<Flight>> allReturnFlights = new HashMap<>();

        // Pre-fetch return flights if not returning to the same airport
        if (!request.getReturnToSameAirport()) {
            for (String destination : request.getDestinationAirports()) {
                List<Flight> returnFlightsForDestination = new ArrayList<>();
                for (String returnDeparture : request.getDepartureAirports()) {
                    returnFlightsForDestination.addAll(scanFlights(destination, returnDeparture,
                            request.getAvailabilityStart(), request.getAvailabilityEnd()));
                }
                allReturnFlights.put(destination, returnFlightsForDestination);
            }
        }

        // Process outbound and return flights
        for (String departure : request.getDepartureAirports()) {
            for (String destination : request.getDestinationAirports()) {
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
