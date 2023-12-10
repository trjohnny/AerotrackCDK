package com.aerotrack.lambda.workflow;

import com.aerotrack.model.entities.Airport;
import com.aerotrack.model.entities.AirportsJsonFile;
import com.aerotrack.utils.clients.ryanair.RyanairClient;
import com.aerotrack.utils.clients.s3.AerotrackS3Client;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static com.aerotrack.utils.Constants.AIRPORTS_OBJECT_NAME;


@Slf4j
@AllArgsConstructor
public class RefreshWorkflow  {

    private enum AirportCode {
        LHR, CDG, FRA, AMS, MAD, BCN, FCO, MUC, LGW, IST,
        CPH, SVO, DME, ORY, ARN, ZRH, VIE, MAN, ATH, LIS,
        OSL, HEL, DUB, BRU, TXL, MXP, GVA, PRG, WAW, DUS,
        BUD, OTP, MLA, STR, TSF, TRS, HAM, VCE,
        VLC, NCE, BLQ, NAP, BHX, GLA, LBA, EDI, BRS, SEN
    }

    private final AerotrackS3Client s3Client;
    private final RyanairClient ryanairClient;

    public static RefreshWorkflow create() {
        return new RefreshWorkflow(AerotrackS3Client.create(), RyanairClient.create());
    }
    public void refreshFlights() throws IOException {

        // Get a random airport using the Ryanair API
        List<Airport> airportList = filterSelectedAirports(ryanairClient.getAvailableAirports());
        List<Airport> currentAirports = getAvailableAirports().getAirports();
        Airport chosenAirport = chooseAirport(currentAirports, airportList);
        AirportsJsonFile file = AirportsJsonFile.builder().airports(updateAirports(currentAirports, chosenAirport)).build();
        s3Client.putJsonObjectToS3(AIRPORTS_OBJECT_NAME, new JSONObject(new ObjectMapper().writer().withDefaultPrettyPrinter().writeValueAsString(file)));
    }

    public Airport chooseAirport(List<Airport> savedAirports, List<Airport> allAirports) {

        Random random = new Random();
        Airport randomAirport = allAirports.get(random.nextInt(allAirports.size()));

        // Check if the airport code exists in savedAirports
        if (savedAirports.stream().anyMatch(airport -> airport.getAirportCode().equals(randomAirport.getAirportCode()))) {
            return savedAirports.stream()
                    .min(Comparator.comparing(this::parseDateTime))
                    .orElse(null);
        }

        return randomAirport;

    }
    public List<Airport> updateAirports(List<Airport> savedAirports, Airport toUpdateOrAdd) {
        List<String> connections = ryanairClient.getAirportConnections(toUpdateOrAdd.getAirportCode());

        for(int i = 0; i < savedAirports.size(); i++) {
            if(savedAirports.get(i).getAirportCode().equals(toUpdateOrAdd.getAirportCode())) {
                savedAirports.remove(i);
                break;
            }
        }

        toUpdateOrAdd.setConnections(filterSelectedAirportsCodes(connections));
        LocalDateTime currentDateTime = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;

        toUpdateOrAdd.setLastUpdatedDateTime(currentDateTime.format(formatter));
        savedAirports.add(toUpdateOrAdd);
        return savedAirports;
    }

    public List<Airport> filterSelectedAirports(List<Airport> airports) {
        return airports.stream()
                .filter(airport -> isInEnum(airport.getAirportCode()))
                .collect(Collectors.toList());
    }

    public List<String> filterSelectedAirportsCodes(List<String> airports) {
        return airports.stream()
                .filter(this::isInEnum)
                .collect(Collectors.toList());
    }

    private boolean isInEnum(String value) {
        return Arrays.stream(AirportCode.values()).anyMatch(e -> e.name().equals(value));
    }

    private LocalDateTime parseDateTime(Airport airport) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
        return LocalDateTime.parse(airport.getLastUpdatedDateTime(), formatter);
    }

    private AirportsJsonFile getAvailableAirports() throws IOException {

        String airportsJson = s3Client.getStringObjectFromS3(AIRPORTS_OBJECT_NAME);
        return new ObjectMapper().readValue(airportsJson, new TypeReference<>() {});
    }

}


