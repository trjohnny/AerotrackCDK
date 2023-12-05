package com.aerotrack.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ScanQueryRequest {
    private Integer minDays;
    private Integer maxDays;
    private String availabilityStart;
    private String availabilityEnd;
    private List<String> departureAirports;
    private List<String> destinationAirports;
    private Boolean returnToSameAirport;
}
