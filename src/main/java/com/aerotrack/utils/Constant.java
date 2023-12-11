package com.aerotrack.utils;

public class Constant {
    public static final String QUERY_LAMBDA = "QueryLambda";
    public static final String FLIGHTS_REFRESH_LAMBDA = "FlightsRefreshLambda";
    public static final String FLIGHTS_REFRESH_LAMBDA_ROLE = "FlightsRefreshLambdaRole";
    public static final String AIRPORTS_REFRESH_LAMBDA = "AirportsRefreshLambda";
    public static final String AIRPORTS_REFRESH_LAMBDA_ROLE = "AirportsRefreshLambdaRole";
    public static final String QUERY_LAMBDA_ROLE = "QueryLambdaRole";

    public static final String REST_API_GATEWAY = "RestApiGateway";
    public static final String USAGE_PLAN = "UsagePlan";
    public static final String API_KEY = "ApiKey";
    public static final String FLIGHTS_TABLE = "FlightsTable";
    public static final String FLIGHTS_REFRESH_EVENT_RULE = "FlightsRefreshEvent";
    public static final String AIRPORTS_REFRESH_EVENT_RULE = "AirportsRefreshEvent";
    public static final String AEROTRACK_BUCKET = "AerotrackBucket";
    public static final Integer API_BURST_LIMIT = 2;
    public static final Integer API_RATE_LIMIT = 2;
    public static final String AIRPORTS_DEPLOYMENT = "AirportsDeployment";
    public static final String REFRESH_CONSTRUCT = "RefreshConstruct";
    public static final String API_CONSTRUCT = "ApiConstruct";
    public static final String DATA_CONSTRUCT = "DataConstruct";
    public static final String GITHUB_USERNAME = "trjohnny";

    public static final String FLIGHT_TABLE_ENV_VAR = "FLIGHT_TABLE";
    public static final String AIRPORTS_BUCKET_ENV_VAR = "AIRPORTS_BUCKET";
    public static final Integer QUERY_LAMBDA_MEMORY_SIZE_MB = 256;
    public static final Integer QUERY_LAMBDA_TIMEOUT_SECONDS = 30;
    public static final Integer FLIGHTS_REFRESH_LAMBDAS_PER_EVENT = 5;

    // Without 5 it does on average one request per second. With 5 it's around one request every 5 seconds.
    public static final Integer FLIGHTS_REFRESH_EVENT_RATE_MINUTES = 15;
    public static final Integer AIRPORTS_REFRESH_EVENT_RATE_MINUTES = 10;


}
