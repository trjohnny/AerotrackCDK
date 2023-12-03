package com.aerotrack.infrastructure;

import software.amazon.awscdk.BundlingOptions;
import software.amazon.awscdk.DockerVolume;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.apigateway.ApiKey;
import software.amazon.awscdk.services.apigateway.Cors;
import software.amazon.awscdk.services.apigateway.CorsOptions;
import software.amazon.awscdk.services.apigateway.LambdaIntegration;
import software.amazon.awscdk.services.apigateway.MethodOptions;
import software.amazon.awscdk.services.apigateway.Resource;
import software.amazon.awscdk.services.apigateway.RestApi;
import software.amazon.awscdk.services.apigateway.ThrottleSettings;
import software.amazon.awscdk.services.apigateway.UsagePlan;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.BillingMode;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.events.RuleProps;
import software.amazon.awscdk.services.events.Schedule;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.FunctionProps;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketEncryption;
import software.amazon.awscdk.services.s3.ObjectOwnership;
import software.amazon.awscdk.services.s3.assets.AssetOptions;
import software.amazon.awscdk.services.s3.deployment.BucketDeployment;
import software.amazon.awscdk.services.s3.deployment.Source;
import software.amazon.awsconstructs.services.eventbridgelambda.EventbridgeToLambda;
import software.constructs.Construct;

import java.util.Arrays;
import java.util.List;

import static java.util.Collections.singletonList;
import static software.amazon.awscdk.BundlingOutput.ARCHIVED;
public class InfraStack extends Stack {

    public static String QUERY_LAMBDA = "QueryLambda";
    public static String REFRESH_LAMBDA = "RefreshLambda";
    public InfraStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public InfraStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        Table flightsTable = Table.Builder.create(this, "FlightsTable")
                .partitionKey(Attribute.builder()
                        .name("direction")
                        .type(AttributeType.STRING)
                        .build())
                .sortKey(Attribute.builder()
                        .name("timestamp")
                        .type(AttributeType.NUMBER)
                        .build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .deletionProtection(false)
                .build();

        RestApi queryRestApi = RestApi.Builder.create(this, "RestApiGateway")
                .defaultCorsPreflightOptions(CorsOptions.builder()
                        .allowOrigins(Cors.ALL_ORIGINS)
                        .allowMethods(Cors.ALL_METHODS)
                        .build())
                .build();

        ApiKey key = ApiKey.Builder.create(this, "ApiKey").build();

        UsagePlan usagePlan = UsagePlan.Builder.create(this, "UsagePlan")
                .throttle(ThrottleSettings.builder()
                        .burstLimit(1)
                        .rateLimit(1)
                        .build())
                .build();

        usagePlan.addApiKey(key);

        BundlingOptions.Builder builderOptions = BundlingOptions.builder()
                .image(Runtime.JAVA_17.getBundlingImage())
                .volumes(singletonList(
                        // Mount local .m2 repo to avoid download all the dependencies again inside the container
                        DockerVolume.builder()
                                .hostPath(System.getProperty("user.home") + "/.m2/")
                                .containerPath("/root/.m2/")
                                .build()
                ))
                .user("root")
                .outputType(ARCHIVED);

        Function queryFunction = new Function(this, "QueryLambda", FunctionProps.builder()
                .runtime(Runtime.JAVA_17)
                .code(Code.fromAsset("src/main/java/com/aerotrack/software/", AssetOptions.builder()
                        .bundling(builderOptions
                                .command(getLambdaPackagingInstructions(QUERY_LAMBDA))
                                .build())
                        .build()))
                .handler("lambda.QueryRequestHandler::handleRequest")
                .memorySize(128)
                .timeout(Duration.seconds(10))
                .logRetention(RetentionDays.ONE_WEEK)
                .build());

        LambdaIntegration lambdaIntegration = new LambdaIntegration(queryFunction);

        // Define a new resource (e.g., '/query')
        Resource queryResource = queryRestApi.getRoot().addResource("QueryResource");

        // Add a method (e.g., GET) to the resource that is integrated with the Lambda function
        queryResource.addMethod("GET", lambdaIntegration, MethodOptions.builder()
                .apiKeyRequired(true)
                .build());

        EventbridgeToLambda refreshEvent = EventbridgeToLambda.Builder.create(this, "EventBridgeRefresh")
                .lambdaFunctionProps(new FunctionProps.Builder()
                        .runtime(Runtime.JAVA_17)
                        .code(Code.fromAsset("src/main/java/com/aerotrack/software/", AssetOptions.builder()
                                .bundling(builderOptions
                                        .command(getLambdaPackagingInstructions(REFRESH_LAMBDA))
                                        .build())
                                .build()))
                        .handler("lambda.RefreshRequestHandler::handleRequest")
                        .build())
                .eventRuleProps(new RuleProps.Builder()
                        .schedule(Schedule.rate(Duration.seconds(60))) // TODO: Change this schedule
                        .build())
                .build();

        Bucket bucket = Bucket.Builder.create(this, "AerotrackBucket")
                .objectOwnership(ObjectOwnership.BUCKET_OWNER_ENFORCED)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .encryption(BucketEncryption.S3_MANAGED)
                .versioned(true)
                .build();

        BucketDeployment airportsDeployment = BucketDeployment.Builder.create(this, "AirportsDeployment")
                .sources(List.of(Source.asset("src/main/java/com/aerotrack/s3resources/")))
                .destinationBucket(bucket)
                .build();
    }

    private List<String> getLambdaPackagingInstructions(String lambda) {
        return Arrays.asList(
                "/bin/sh",
                "-c",
                String.format("cd %s ", lambda) +
                        "&& mvn clean install " +
                        String.format("&& cp /asset-input/%s/target/%s-1.0-SNAPSHOT.jar /asset-output/", lambda, lambda)
        );
    }
}