package com.aerotrack.lambda.workflow;

import com.aerotrack.model.entities.Airport;
import com.aerotrack.model.entities.Flight;
import com.aerotrack.model.entities.Trip;
import com.aerotrack.model.protocol.ScanQueryRequest;
import com.aerotrack.model.protocol.ScanQueryResponse;
import com.aerotrack.model.entities.AirportsJsonFile;
import com.aerotrack.utils.clients.dynamodb.AerotrackDynamoDbClient;
import com.aerotrack.utils.clients.s3.AerotrackS3Client;
import com.aerotrack.utils.Constants;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

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
import java.util.Optional;
import java.util.Set;


@Slf4j
public class QueryLambdaWorkflow {
    private static final long TRIPS_RETURN_LIMIT = 10000;
    private final AerotrackDynamoDbClient dynamoDbClient;
    private final AerotrackS3Client s3Client;
    private final ObjectMapper objectMapper;


    public QueryLambdaWorkflow(AerotrackDynamoDbClient dynamoDbClient, AerotrackS3Client s3Client) {
        this.dynamoDbClient = dynamoDbClient;
        this.s3Client = s3Client;
        this.objectMapper = new ObjectMapper();
    }

    public ScanQueryResponse queryAndProcessFlights(Integer minDays, Integer maxDays, String availabilityStart, String availabilityEnd,
                                                    List<String> departureAirports, List<String> destinationAirports, Integer maxChanges,
                                                    Optional<Integer> minTimeBetweenChangesHours, Optional<Integer> maxTimeBetweenChangesHours,
                                                    Boolean returnToSameAirport) throws IOException {
        AirportsJsonFile airportsJsonFile = objectMapper.readValue(s3Client.getStringObjectFromS3(Constants.AIRPORTS_OBJECT_NAME), AirportsJsonFile.class);
        List<Airport> airportList = airportsJsonFile.getAirports();

        // This map is used to check the existence of every airport in the request and their possible connections.
        // In this way we limit the number of calls to DynamoDB
        Map<String, Set<String>> airportsConnections = getAirportConnectionsMap(airportList);

        List<Trip> trips = new ArrayList<>();
        Map<String, List<Flight>> allReturnFlights = new HashMap<>();

        // Pre-fetch return flights if not returning to the same airport
        if (!returnToSameAirport) {
            for (String destination : destinationAirports) {
                if (! airportsConnections.containsKey(destination))
                    continue;

                List<Flight> returnFlightsForDestination = new ArrayList<>();
                for (String returnDeparture : departureAirports) {
                    if (! airportsConnections.get(destination).contains(returnDeparture))
                        continue;

                    returnFlightsForDestination.addAll(dynamoDbClient.scanFlightsBetweenDates(destination, returnDeparture,
                            availabilityStart, availabilityEnd));
                }
                allReturnFlights.put(destination, returnFlightsForDestination);
            }
        }

        log.info("Processing outbound and return flights...");
        // Process outbound and return flights
        for (String departure : departureAirports) {
            if (! airportsConnections.containsKey(departure))
                continue;

            for (String destination : destinationAirports) {
                if (! airportsConnections.get(departure).contains(destination))
                    continue;

                List<Flight> outboundFlights = dynamoDbClient.scanFlightsBetweenDates(departure, destination,
                        availabilityStart, availabilityEnd);

                log.debug(outboundFlights.toString());

                List<Flight> returnFlights = returnToSameAirport ?
                        dynamoDbClient.scanFlightsBetweenDates(destination, departure, availabilityStart, availabilityEnd) :
                        allReturnFlights.getOrDefault(destination, new ArrayList<>());

                log.debug(returnFlights.toString());

                // Find matching pairs
                for (Flight outboundFlight : outboundFlights) {
                    for (Flight returnFlight : returnFlights) {
                        int duration = calculateDuration(outboundFlight, returnFlight);
                        if (duration < minDays) continue;
                        if (duration > maxDays) break;

                        int totalPrice = (int) (outboundFlight.getPrice() + returnFlight.getPrice());
                        trips.add(new Trip(List.of(outboundFlight), List.of(returnFlight), totalPrice));
                    }
                }
            }
        }

        log.info("Sorting pairs... Size: " + trips.size());
        List<Trip> sortedPairs = trips.stream()
                .sorted(Comparator.comparing(Trip::getTotalPrice))
                .limit(TRIPS_RETURN_LIMIT)
                .toList();


        return ScanQueryResponse.builder()
                .trips(sortedPairs)
                .build();
    }

    private Map<String, Set<String>> getAirportConnectionsMap(List<Airport> airportList) {
        Map<String, Set<String>> airportsConnections = new HashMap<>();

        for (Airport airport : airportList) {
            String airportCode = airport.getAirportCode();
            Set<String> connectionsSet = new HashSet<>(airport.getConnections());
            airportsConnections.put(airportCode, connectionsSet);
        }

        return airportsConnections;
    }

    private int calculateDuration(Flight outboundFlight, Flight returnFlight) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;

        LocalDateTime departureDateTime = LocalDateTime.parse(outboundFlight.getDepartureDateTime(), formatter);
        LocalDateTime returnDateTime = LocalDateTime.parse(returnFlight.getDepartureDateTime(), formatter);

        Duration duration = Duration.between(departureDateTime, returnDateTime);
        return (int) duration.toDays();
    }
}
