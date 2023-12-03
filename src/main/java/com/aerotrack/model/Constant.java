package com.aerotrack.model;

public class Constant {
    public static String QUERY_LAMBDA = "QueryLambda";
    public static String REFRESH_LAMBDA = "RefreshLambda";
    public static String REST_API_GATEWAY = "RestApiGateway";
    public static String USAGE_PLAN = "UsagePlan";
    public static String API_KEY = "ApiKey";
    public static String FLIGHTS_TABLE = "FlightsTable";
    public static String REFRESH_EVENT_RULE = "RefreshEvent";
    public static String AEROTRACK_BUCKET = "AerotrackBucket";
    public static Integer API_BURST_LIMIT = 2;
    public static Integer API_RATE_LIMIT = 2;
    public static String AIRPORTS_DEPLOYMENT = "AirportsDeployment";
    public static String REFRESH_CONSTRUCT = "RefreshConstruct";
    public static String API_CONSTRUCT = "ApiConstruct";
    public static String DATA_CONSTRUCT = "DataConstruct";
    public static Integer QUERY_LAMBDA_MEMORY_SIZE = 128;
    public static Integer QUERY_LAMBDA_TIMEOUT_SECONDS = 10;
    public static Integer REFRESH_EVENT_RATE_SECONDS = 60; // Multiple of 60

}
