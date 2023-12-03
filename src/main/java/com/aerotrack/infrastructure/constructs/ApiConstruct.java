package com.aerotrack.infrastructure.constructs;

import com.aerotrack.utils.Utils;
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
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.FunctionProps;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.s3.assets.AssetOptions;
import software.amazon.jsii.JsiiObjectRef;
import software.constructs.Construct;

import static com.aerotrack.model.Constant.API_BURST_LIMIT;
import static com.aerotrack.model.Constant.API_KEY;
import static com.aerotrack.model.Constant.API_RATE_LIMIT;
import static com.aerotrack.model.Constant.QUERY_LAMBDA;
import static com.aerotrack.model.Constant.QUERY_LAMBDA_MEMORY_SIZE;
import static com.aerotrack.model.Constant.QUERY_LAMBDA_TIMEOUT_SECONDS;
import static com.aerotrack.model.Constant.REST_API_GATEWAY;
import static com.aerotrack.model.Constant.USAGE_PLAN;

public class ApiConstruct extends Construct {
    protected ApiConstruct(JsiiObjectRef objRef) {
        super(objRef);
    }

    protected ApiConstruct(InitializationMode initializationMode) {
        super(initializationMode);
    }

    public ApiConstruct(@NotNull Construct scope, @NotNull String id) {
        super(scope, id);

        RestApi queryRestApi = RestApi.Builder.create(this, Utils.getResourceName(REST_API_GATEWAY))
                .defaultCorsPreflightOptions(CorsOptions.builder()
                        .allowOrigins(Cors.ALL_ORIGINS)
                        .allowMethods(Cors.ALL_METHODS)
                        .build())
                .build();

        ApiKey key = ApiKey.Builder.create(this, API_KEY).build();

        UsagePlan usagePlan = UsagePlan.Builder.create(this, Utils.getResourceName(USAGE_PLAN))
                .throttle(ThrottleSettings.builder()
                        .burstLimit(API_BURST_LIMIT)
                        .rateLimit(API_RATE_LIMIT)
                        .build())
                .build();

        usagePlan.addApiKey(key);

        Function queryFunction = new Function(this, Utils.getResourceName(QUERY_LAMBDA), FunctionProps.builder()
                .runtime(Runtime.JAVA_17)
                .code(Code.fromAsset("src/main/java/com/aerotrack/software/", AssetOptions.builder()
                        .bundling(Utils.getLambdaBuilderOptions()
                                .command(Utils.getLambdaPackagingInstructions(QUERY_LAMBDA))
                                .build())
                        .build()))
                .handler("lambda.QueryRequestHandler::handleRequest")
                .memorySize(QUERY_LAMBDA_MEMORY_SIZE)
                .timeout(Duration.seconds(QUERY_LAMBDA_TIMEOUT_SECONDS))
                .logRetention(RetentionDays.ONE_DAY)
                .build());

        LambdaIntegration lambdaIntegration = new LambdaIntegration(queryFunction);

        Resource queryResource = queryRestApi.getRoot().addResource("scan");

        // Add a method (e.g., GET) to the resource that is integrated with the Lambda function
        queryResource.addMethod("GET", lambdaIntegration, MethodOptions.builder()
                .apiKeyRequired(true)
                .build());
    }
}