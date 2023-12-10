package com.aerotrack.infrastructure.constructs;

import com.aerotrack.utils.Utils;
import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.events.Rule;
import software.amazon.awscdk.services.events.Schedule;
import software.amazon.awscdk.services.events.targets.LambdaFunction;
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
import software.amazon.jsii.JsiiObjectRef;
import software.constructs.Construct;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import com.aerotrack.utils.Constant;
import static com.aerotrack.utils.Utils.isPersonalDeployment;


public class RefreshConstruct extends Construct {


    public RefreshConstruct(@NotNull Construct scope, @NotNull String id, Bucket airportsBucket, Table flightsTable) {
        super(scope, id);


        Function flightsRefreshLambda = createRefreshLambda(Constant.FLIGHTS_REFRESH_LAMBDA,
                Constant.FLIGHTS_REFRESH_LAMBDA_ROLE,
                new HashMap<>() {
                    {
                        put(Constant.FLIGHT_TABLE_ENV_VAR, flightsTable.getTableName());
                        put(Constant.AIRPORTS_BUCKET_ENV_VAR, airportsBucket.getBucketName());
                    }
                });

        airportsBucket.grantRead(Objects.requireNonNull(flightsRefreshLambda.getRole()));
        flightsTable.grantWriteData(Objects.requireNonNull(flightsRefreshLambda.getRole()));

        Function airportsRefreshLambda = createRefreshLambda(Constant.AIRPORTS_REFRESH_LAMBDA,
                Constant.AIRPORTS_REFRESH_LAMBDA_ROLE,
                new HashMap<>() {
                    {
                        put(Constant.AIRPORTS_BUCKET_ENV_VAR, airportsBucket.getBucketName());
                    }
                });

        airportsBucket.grantReadWrite(Objects.requireNonNull(airportsRefreshLambda.getRole()));

        if(!isPersonalDeployment())
        {
            Rule rule = Rule.Builder.create(this, Utils.getResourceName(Constant.FLIGHTS_REFRESH_EVENT_RULE))
                    .schedule(Schedule.rate(Duration.minutes(Constant.FLIGHTS_REFRESH_EVENT_RATE_MINUTES)))
                    .build();

            for (int i = 0; i < Constant.FLIGHTS_REFRESH_LAMBDAS_PER_EVENT; i++) {
                rule.addTarget(new LambdaFunction(flightsRefreshLambda));
            }

            Rule.Builder.create(this, Utils.getResourceName(Constant.AIRPORTS_REFRESH_EVENT_RULE))
                    .schedule(Schedule.rate(Duration.minutes(Constant.AIRPORTS_REFRESH_EVENT_RATE_MINUTES)))
                    .targets(List.of(new LambdaFunction(airportsRefreshLambda)))
                    .build();
        }

    }

    private Function createRefreshLambda(String lambdaName, String roleName, HashMap<String, String> env) {
        Role lambdaRole = Role.Builder.create(this, Utils.getResourceName(roleName))
                .assumedBy(new ServicePrincipal("lambda.amazonaws.com"))
                .managedPolicies(List.of(
                        ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaBasicExecutionRole")
                ))
                .build();

        // Define the Lambda function
        return Function.Builder.create(this, Utils.getResourceName(lambdaName))
                .runtime(Runtime.JAVA_17)
                .code(Code.fromAsset("src/main/java/com/aerotrack/infrastructure/lambda", AssetOptions.builder()
                        .bundling(Utils.getLambdaBuilderOptions()
                                .command(Utils.getLambdaPackagingInstructions(lambdaName))
                                .build())
                        .build()))
                .environment(env)
                .role(lambdaRole)
                .timeout(Duration.minutes(5))
                .memorySize(256)
                .logRetention(RetentionDays.ONE_DAY)
                .handler("com.aerotrack.lambda.RefreshRequestHandler::handleRequest")
                .build();


    }


}
