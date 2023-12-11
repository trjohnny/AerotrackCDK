package com.aerotrack.lambda.workflow;

import com.aerotrack.model.entities.Airport;
import com.aerotrack.model.entities.Flight;
import com.aerotrack.model.entities.FlightPair;
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
import java.util.Set;


@Slf4j
public class QueryLambdaWorkflow {
    private final AerotrackDynamoDbClient dynamoDbClient;
    private final AerotrackS3Client s3Client;
    private final ObjectMapper objectMapper;


    public QueryLambdaWorkflow(AerotrackDynamoDbClient dynamoDbClient, AerotrackS3Client s3Client) {
        this.dynamoDbClient = dynamoDbClient;
        this.s3Client = s3Client;
        this.objectMapper = new ObjectMapper();
    }

    public ScanQueryResponse queryAndProcessFlights(ScanQueryRequest request) throws IOException {
        AirportsJsonFile airportsJsonFile = objectMapper.readValue(s3Client.getStringObjectFromS3(Constants.AIRPORTS_OBJECT_NAME), AirportsJsonFile.class);
        List<Airport> airportList = airportsJsonFile.getAirports();

        // This map is used to check the existence of every airport in the request and their possible connections.
        // In this way we limit the number of calls to DynamoDB
        Map<String, Set<String>> airportsConnections = getAirportConnectionsMap(airportList);

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

                    returnFlightsForDestination.addAll(dynamoDbClient.scanFlightsBetweenDates(destination, returnDeparture,
                            request.getAvailabilityStart(), request.getAvailabilityEnd()));
                }
                allReturnFlights.put(destination, returnFlightsForDestination);
            }
        }

        log.info("Processing outbound and return flights...");
        // Process outbound and return flights
        for (String departure : request.getDepartureAirports()) {
            if (! airportsConnections.containsKey(departure))
                continue;

            for (String destination : request.getDestinationAirports()) {
                if (! airportsConnections.get(departure).contains(destination))
                    continue;

                List<Flight> outboundFlights = dynamoDbClient.scanFlightsBetweenDates(departure, destination,
                        request.getAvailabilityStart(), request.getAvailabilityEnd());

                log.debug(outboundFlights.toString());

                List<Flight> returnFlights = request.getReturnToSameAirport() ?
                        dynamoDbClient.scanFlightsBetweenDates(destination, departure, request.getAvailabilityStart(), request.getAvailabilityEnd()) :
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

        log.info("Sorting pairs...");
        List<FlightPair> sortedPairs = flightPairs.stream()
                .sorted(Comparator.comparing(FlightPair::getTotalPrice))
                .limit(10)
                .toList();

        return ScanQueryResponse.builder()
                .flightPairs(sortedPairs)
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
