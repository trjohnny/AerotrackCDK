package com.aerotrack.infrastructure.constructs;

import com.aerotrack.common.InfraUtils;
import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.services.cloudwatch.Dashboard;
import software.amazon.awscdk.services.cloudwatch.GraphWidget;
import software.amazon.awscdk.services.cloudwatch.GraphWidgetProps;
import software.amazon.awscdk.services.cloudwatch.Metric;
import software.amazon.awscdk.services.cloudwatch.Unit;
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
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.assets.AssetOptions;
import software.constructs.Construct;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import com.aerotrack.common.Constants;
import static com.aerotrack.common.InfraUtils.isPersonalDeployment;


public class RefreshConstruct extends Construct {


    private static final Integer AIRPORTS_REFRESH_EVENT_RATE_HOURS = 1;
    private static final Integer FLIGHTS_REFRESH_EVENT_RATE_MINUTES = 15;
    private static final Integer FLIGHTS_REFRESH_LAMBDAS_PER_EVENT = 5;

    public RefreshConstruct(@NotNull Construct scope, @NotNull String id, Bucket airportsBucket, Table flightsTable) {
        super(scope, id);


        Function flightsRefreshLambda = getRefreshLambda(Constants.FLIGHTS_REFRESH_LAMBDA,
                "FlightsRefreshLambdaRole",
                new HashMap<>() {
                    {
                        put(Constants.FLIGHT_TABLE_ENV_VAR, flightsTable.getTableName());
                        put(Constants.AIRPORTS_BUCKET_ENV_VAR, airportsBucket.getBucketName());
                    }
                });

        airportsBucket.grantRead(Objects.requireNonNull(flightsRefreshLambda.getRole()));
        flightsTable.grantWriteData(Objects.requireNonNull(flightsRefreshLambda.getRole()));

        Function airportsRefreshLambda = getRefreshLambda(Constants.AIRPORTS_REFRESH_LAMBDA,
                "AirportsRefreshLambdaRole",
                new HashMap<>() {
                    {
                        put(Constants.AIRPORTS_BUCKET_ENV_VAR, airportsBucket.getBucketName());
                    }
                });

        airportsBucket.grantReadWrite(Objects.requireNonNull(airportsRefreshLambda.getRole()));

        if(!isPersonalDeployment())
        {
            Rule rule = Rule.Builder.create(this, InfraUtils.getResourceName("FlightsRefreshEvent"))
                    .schedule(Schedule.rate(Duration.minutes(FLIGHTS_REFRESH_EVENT_RATE_MINUTES)))
                    .build();

            for (int i = 0; i < FLIGHTS_REFRESH_LAMBDAS_PER_EVENT; i++) {
                rule.addTarget(new LambdaFunction(flightsRefreshLambda));
            }

            Rule.Builder.create(this, InfraUtils.getResourceName("AirportsRefreshEvent"))
                    .schedule(Schedule.rate(Duration.hours(AIRPORTS_REFRESH_EVENT_RATE_HOURS)))
                    .targets(List.of(new LambdaFunction(airportsRefreshLambda)))
                    .build();

            Metric apiCallMetric = Metric.Builder.create()
                    .namespace(com.aerotrack.utils.Constants.METRIC_REFRESH_FLIGHTS_NAMESPACE)
                    .metricName(com.aerotrack.utils.Constants.METRIC_REFRESH_FLIGHTS_API_CALLS)
                    .statistic("Sum")
                    .unit(Unit.COUNT)
                    .period(Duration.hours(1))
                    .build();

            Dashboard dashboard = Dashboard.Builder.create(this, "AerotrackDashboard")
                    .dashboardName("AerotrackDashboard")
                    .defaultInterval(Duration.hours(1))
                    .build();

            dashboard.addWidgets(new GraphWidget(GraphWidgetProps.builder()
                    .title("Ryanair API Calls")
                    .left(List.of(apiCallMetric))
                    .width(20)
                    .height(10)
                    .build()));
        }
    }

    private Function getRefreshLambda(String lambdaName, String roleName, HashMap<String, String> env) {
        Role lambdaRole = Role.Builder.create(this, InfraUtils.getResourceName(roleName))
                .assumedBy(new ServicePrincipal("lambda.amazonaws.com"))
                .managedPolicies(List.of(
                        ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaBasicExecutionRole"),
                        ManagedPolicy.fromAwsManagedPolicyName("CloudWatchFullAccess")
                ))
                .build();

        // Define the Lambda function
        return Function.Builder.create(this, InfraUtils.getResourceName(lambdaName))
                .runtime(Runtime.JAVA_17)
                .code(Code.fromAsset("src/main/java/com/aerotrack/infrastructure/lambda", AssetOptions.builder()
                        .bundling(InfraUtils.getLambdaBuilderOptions()
                                .command(InfraUtils.getLambdaPackagingInstructions(lambdaName))
                                .build())
                        .build()))
                .environment(env)
                .role(lambdaRole)
                .timeout(Duration.minutes(5))
                .memorySize(256)
                .logRetention(RetentionDays.ONE_DAY)
                .handler(InfraUtils.getLambdaRequestHandler(lambdaName))
                .build();


    }

}
