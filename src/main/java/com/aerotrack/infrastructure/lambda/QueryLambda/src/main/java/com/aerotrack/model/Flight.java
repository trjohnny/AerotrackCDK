package com.aerotrack.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class Flight {
    private String direction;
    private String departureDateTime;
    private String arrivalDateTime;
    private String flightNumber;
    private double price;

    @DynamoDbSortKey
    public String getDepartureDateTime() { return departureDateTime; }
    public void setDepartureDateTime(String departureDateTime) { this.departureDateTime = departureDateTime; }

    @DynamoDbPartitionKey
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
}
