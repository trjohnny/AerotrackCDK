package com.aerotrack.infrastructure.constructs;

import com.aerotrack.common.InfraUtils;
import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.services.apigateway.ApiKey;
import software.amazon.awscdk.services.apigateway.Cors;
import software.amazon.awscdk.services.apigateway.CorsOptions;
import software.amazon.awscdk.services.apigateway.LambdaIntegration;
import software.amazon.awscdk.services.apigateway.MethodOptions;
import software.amazon.awscdk.services.apigateway.Resource;
import software.amazon.awscdk.services.apigateway.RestApi;
import software.amazon.awscdk.services.apigateway.ThrottleSettings;
import software.amazon.awscdk.services.apigateway.UsagePlan;
import software.amazon.awscdk.services.apigateway.UsagePlanPerApiStage;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.FunctionProps;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.assets.AssetOptions;
import software.constructs.Construct;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.aerotrack.common.Constants;


public class  ApiConstruct extends Construct {

    private static final String SCAN_RESOURCE = "scan";
    private static final Integer API_DEFAULT_MEMORY_SIZE = 2048;
    private static final Integer API_DEFAULT_TIMEOUT_SECONDS = 30;

    public ApiConstruct(@NotNull Construct scope, @NotNull String id, Bucket airportsBucket, Table flightsTable) {
        super(scope, id);

        RestApi queryRestApi = RestApi.Builder.create(this, InfraUtils.getResourceName("RestApiGateway"))
                .defaultCorsPreflightOptions(CorsOptions.builder()
                        .allowOrigins(Cors.ALL_ORIGINS)
                        .allowMethods(Cors.ALL_METHODS)
                        .build())
                .build();

        ApiKey key = ApiKey.Builder.create(this, "ApiKey").build();

        UsagePlan usagePlan = UsagePlan.Builder.create(this, InfraUtils.getResourceName("UsagePlan"))
                .throttle(ThrottleSettings.builder()
                        .burstLimit(5)
                        .rateLimit(5)
                        .build())
                .build();

        usagePlan.addApiStage(UsagePlanPerApiStage.builder()
                .stage(queryRestApi.getDeploymentStage())
                .build());

        usagePlan.addApiKey(key);

        Function queryFunction = getApiLambda(Constants.QUERY_LAMBDA,
                new HashMap<>() {
                    {
                        put(Constants.FLIGHT_TABLE_ENV_VAR, flightsTable.getTableName());
                        put(Constants.AIRPORTS_BUCKET_ENV_VAR, airportsBucket.getBucketName());
                    }
                });

        airportsBucket.grantRead(Objects.requireNonNull(queryFunction.getRole()));
        flightsTable.grantReadData(Objects.requireNonNull(queryFunction.getRole()));

        Resource queryResource = queryRestApi.getRoot().addResource(SCAN_RESOURCE);

        queryResource.addMethod("POST", new LambdaIntegration(queryFunction), MethodOptions.builder()
                .apiKeyRequired(true)
                .build());


        Function fetchAirportsFunction = getApiLambda(Constants.FETCH_AIRPORTS_LAMBDA,
                new HashMap<>() {
                    {
                        put(Constants.AIRPORTS_BUCKET_ENV_VAR, airportsBucket.getBucketName());
                    }
                });

        airportsBucket.grantRead(Objects.requireNonNull(fetchAirportsFunction.getRole()));

        Resource airportsResource = queryRestApi.getRoot().addResource("airports");
        Resource getRyanairAirportsResource = airportsResource.addResource("ryanair");
        Resource getWizzairAirportsResource = airportsResource.addResource("wizzair");
        Resource getAirportsResource = airportsResource.addResource("merged");

        getRyanairAirportsResource.addMethod("GET", new LambdaIntegration(fetchAirportsFunction), MethodOptions.builder()
                .apiKeyRequired(true)
                .build());

        getWizzairAirportsResource.addMethod("GET", new LambdaIntegration(fetchAirportsFunction), MethodOptions.builder()
                .apiKeyRequired(true)
                .build());

        getAirportsResource.addMethod("GET", new LambdaIntegration(fetchAirportsFunction), MethodOptions.builder()
                .apiKeyRequired(true)
                .build());
    }

    private Function getApiLambda(String functionName, Map<String, String> env) {
        Role lambdaRole = Role.Builder.create(this, String.format("%sRole", InfraUtils.getResourceName(functionName)))
                .assumedBy(new ServicePrincipal("lambda.amazonaws.com"))
                .managedPolicies(List.of(
                        ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaBasicExecutionRole")
                ))
                .build();

        return new Function(this, InfraUtils.getResourceName(functionName), FunctionProps.builder()
                .runtime(Runtime.JAVA_17)
                .code(Code.fromAsset("src/main/java/com/aerotrack/infrastructure/lambda", AssetOptions.builder()
                        .bundling(InfraUtils.getLambdaBuilderOptions()
                                .command(InfraUtils.getLambdaPackagingInstructions(functionName))
                                .build())
                        .build()))
                .environment(env)
                .handler(InfraUtils.getLambdaRequestHandler(functionName))
                .role(lambdaRole)
                .memorySize(API_DEFAULT_MEMORY_SIZE)
                .timeout(Duration.seconds(API_DEFAULT_TIMEOUT_SECONDS))
                .logRetention(RetentionDays.ONE_DAY)
                .build());
    }
}

