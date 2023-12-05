package com.aerotrack.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@ToString
public class FlightPair {
    private Flight outboundFlight;
    private Flight returnFlight;
    private Integer totalPrice;
}