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

import com.aerotrack.common.Constants;


public class  ApiConstruct extends Construct {

    private static final String SCAN_RESOURCE = "scan";

    public ApiConstruct(@NotNull Construct scope, @NotNull String id, Bucket airportsBucket, Table flightsTable) {
        super(scope, id);

        Role lambdaRole = Role.Builder.create(this, InfraUtils.getResourceName(Constants.QUERY_LAMBDA_ROLE))
                .assumedBy(new ServicePrincipal("lambda.amazonaws.com"))
                .managedPolicies(List.of(
                        ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaBasicExecutionRole")
                ))
                .build();

        airportsBucket.grantRead(lambdaRole);
        flightsTable.grantReadData(lambdaRole);

        RestApi queryRestApi = RestApi.Builder.create(this, InfraUtils.getResourceName(Constants.REST_API_GATEWAY))
                .defaultCorsPreflightOptions(CorsOptions.builder()
                        .allowOrigins(Cors.ALL_ORIGINS)
                        .allowMethods(Cors.ALL_METHODS)
                        .build())
                .build();

        ApiKey key = ApiKey.Builder.create(this, Constants.API_KEY).build();

        UsagePlan usagePlan = UsagePlan.Builder.create(this, InfraUtils.getResourceName(Constants.USAGE_PLAN))
                .throttle(ThrottleSettings.builder()
                        .burstLimit(Constants.API_BURST_LIMIT)
                        .rateLimit(Constants.API_RATE_LIMIT)
                        .build())
                .build();

        usagePlan.addApiStage(UsagePlanPerApiStage.builder()
                .stage(queryRestApi.getDeploymentStage())
                .build());

        usagePlan.addApiKey(key);

        Function queryFunction = new Function(this, InfraUtils.getResourceName(Constants.QUERY_LAMBDA), FunctionProps.builder()
                .runtime(Runtime.JAVA_17)
                .code(Code.fromAsset("src/main/java/com/aerotrack/infrastructure/lambda", AssetOptions.builder()
                        .bundling(InfraUtils.getLambdaBuilderOptions()
                                .command(InfraUtils.getLambdaPackagingInstructions(Constants.QUERY_LAMBDA))
                                .build())
                        .build()))
                .environment(new HashMap<>() {
                    {
                        put(Constants.FLIGHT_TABLE_ENV_VAR, flightsTable.getTableName());
                        put(Constants.AIRPORTS_BUCKET_ENV_VAR, airportsBucket.getBucketName());
                    }
                })
                .handler("com.aerotrack.lambda.QueryRequestHandler::handleRequest")
                .role(lambdaRole)
                .memorySize(Constants.QUERY_LAMBDA_MEMORY_SIZE_MB)
                .timeout(Duration.seconds(Constants.QUERY_LAMBDA_TIMEOUT_SECONDS))
                .logRetention(RetentionDays.ONE_DAY)
                .build());

        Resource queryResource = queryRestApi.getRoot().addResource(SCAN_RESOURCE);

        queryResource.addMethod("POST", new LambdaIntegration(queryFunction), MethodOptions.builder()
                .apiKeyRequired(true)
                .build());


        Function fetchAirportsFunction = new Function(this, InfraUtils.getResourceName(Constants.FETCH_AIRPORTS_LAMBDA), FunctionProps.builder()
                .runtime(Runtime.JAVA_17)
                .code(Code.fromAsset("src/main/java/com/aerotrack/infrastructure/lambda", AssetOptions.builder()
                        .bundling(InfraUtils.getLambdaBuilderOptions()
                                .command(InfraUtils.getLambdaPackagingInstructions(Constants.FETCH_AIRPORTS_LAMBDA)) //
                                .build())
                        .build()))
                .environment(new HashMap<>() {
                    {
                        put(Constants.AIRPORTS_BUCKET_ENV_VAR, airportsBucket.getBucketName());
                    }
                })
                .handler("com.aerotrack.lambda.FetchAirportsRequestHandler::handleRequest")
                .role(lambdaRole)
                .memorySize(2048)
                .timeout(Duration.seconds(30))
                .logRetention(RetentionDays.ONE_DAY)
                .build());

        Resource getAirportsResource = queryRestApi.getRoot().addResource("airports");

        getAirportsResource.addMethod("GET", new LambdaIntegration(fetchAirportsFunction), MethodOptions.builder()
                .apiKeyRequired(true)
                .build());

    }
}

