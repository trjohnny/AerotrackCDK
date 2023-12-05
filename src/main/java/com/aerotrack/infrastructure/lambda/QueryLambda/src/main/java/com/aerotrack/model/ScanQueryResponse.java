package com.aerotrack.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ScanQueryResponse {
    List<FlightPair> flightPairs;
}
