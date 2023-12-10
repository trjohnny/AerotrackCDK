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
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.assets.AssetOptions;
import software.amazon.jsii.JsiiObjectRef;
import software.constructs.Construct;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.aerotrack.utils.Constant;
import static com.aerotrack.utils.Utils.isPersonalDeployment;


public class RefreshConstruct extends Construct {


    public RefreshConstruct(@NotNull Construct scope, @NotNull String id, Bucket directionBucket, Table flightsTable) {
        super(scope, id);

        Role lambdaRole = Role.Builder.create(this, Utils.getResourceName(Constant.REFRESH_LAMBDA_ROLE))
                .assumedBy(new ServicePrincipal("lambda.amazonaws.com"))
                .managedPolicies(List.of(
                        ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaBasicExecutionRole")
                ))
                .build();

        directionBucket.grantRead(lambdaRole);
        flightsTable.grantWriteData(lambdaRole);

        // Define the Lambda function
        Function refreshLambdaFunction = Function.Builder.create(this, Utils.getResourceName(Constant.REFRESH_LAMBDA))
                .runtime(Runtime.JAVA_17)
                .code(Code.fromAsset("src/main/java/com/aerotrack/infrastructure/lambda", AssetOptions.builder()
                        .bundling(Utils.getLambdaBuilderOptions()
                                .command(Utils.getLambdaPackagingInstructions(Constant.REFRESH_LAMBDA))
                                .build())
                        .build()))
                .environment(new HashMap<>() {
                    {
                        put(Constant.FLIGHT_TABLE_ENV_VAR, flightsTable.getTableName());
                        put(Constant.AIRPORTS_BUCKET_ENV_VAR, directionBucket.getBucketName());
                    }
                })
                .role(lambdaRole)
                .timeout(Duration.minutes(5))
                .memorySize(256)
                .handler("com.aerotrack.lambda.RefreshRequestHandler::handleRequest")
                .build();

        if(!isPersonalDeployment())
        {
            List<LambdaFunction> lambdas = new ArrayList<>();
            for(int i = 0; i < Constant.REFRESH_LAMBDAS_PER_EVENT; i++)
                lambdas.add(new LambdaFunction(refreshLambdaFunction));
            Rule.Builder.create(this, Utils.getResourceName(Constant.REFRESH_EVENT_RULE))
                    .schedule(Schedule.rate(Duration.seconds(Constant.REFRESH_EVENT_RATE_SECONDS)))
                    .targets(lambdas)
                    .build();
        }
    }
}
