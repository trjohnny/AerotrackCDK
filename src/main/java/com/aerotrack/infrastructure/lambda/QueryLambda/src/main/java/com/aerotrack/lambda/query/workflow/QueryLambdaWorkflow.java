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
import java.util.List;

@Slf4j
public class QueryLambdaWorkflow {
    private final DynamoDbTable<Flight> flightTable;
    static final TableSchema<Flight> FLIGHT_TABLE_SCHEMA = TableSchema.fromClass(Flight.class);


    public QueryLambdaWorkflow(DynamoDbEnhancedClient dynamoDbEnhancedClient) {
        this.flightTable = dynamoDbEnhancedClient.table(
                "Giovanni-InfraStack-DataConstructGiovanniFlightsTable4DEB1F4C-9KRMSFT8UPQO",
                FLIGHT_TABLE_SCHEMA);
    }

    public ScanQueryResponse queryAndProcessFlights(ScanQueryRequest request) {
        List<FlightPair> flightPairs = new ArrayList<>();

        // Populate lists for outbound and return flights
        for (String departure : request.getDepartureAirports()) {
            for (String destination : request.getDestinationAirports()) {

                List<Flight> outboundFlights = scanFlights(departure, destination,
                        request.getAvailabilityStart(),
                        request.getAvailabilityEnd());

                log.info("Outbound flights:  [{}]", outboundFlights);

                List<Flight> returnFlights = request.getReturnToSameAirport() ? scanFlights(destination, departure,
                        request.getAvailabilityStart(),
                        request.getAvailabilityEnd()) :
                        new ArrayList<>();

                log.info("Return flights:  [{}]", returnFlights);

                // Find matching pairs
                for (Flight outboundFlight : outboundFlights) {
                    for (Flight returnFlight : returnFlights) {
                        int duration = calculateDuration(outboundFlight, returnFlight);

                        if (duration >= request.getMinDays() && duration <= request.getMaxDays()) {
                            int totalPrice = (int) (outboundFlight.getPrice() + returnFlight.getPrice());
                            flightPairs.add(new FlightPair(outboundFlight, returnFlight, totalPrice));
                        }
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

    public List<Flight> scanFlights(String departure, String destination, String availabilityStart, String availabilityEnd) {
        String partitionKey = departure + "-" + destination;
        Key startKey = Key.builder().partitionValue(partitionKey).sortValue(availabilityStart).build();
        Key endKey = Key.builder().partitionValue(partitionKey).sortValue(availabilityEnd).build();

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
