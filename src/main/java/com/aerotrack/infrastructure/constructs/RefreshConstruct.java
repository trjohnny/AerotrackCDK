package com.aerotrack.infrastructure.constructs;

import com.aerotrack.utils.Utils;
import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.services.events.Rule;
import software.amazon.awscdk.services.events.Schedule;
import software.amazon.awscdk.services.events.targets.LambdaFunction;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.s3.assets.AssetOptions;
import software.amazon.jsii.JsiiObjectRef;
import software.constructs.Construct;

import java.util.List;

import static com.aerotrack.utils.Constant.REFRESH_EVENT_RATE_SECONDS;
import static com.aerotrack.utils.Constant.REFRESH_EVENT_RULE;
import static com.aerotrack.utils.Constant.REFRESH_LAMBDA;

public class RefreshConstruct extends Construct {
    protected RefreshConstruct(JsiiObjectRef objRef) {
        super(objRef);
    }

    protected RefreshConstruct(InitializationMode initializationMode) {
        super(initializationMode);
    }

    public RefreshConstruct(@NotNull Construct scope, @NotNull String id) {
        super(scope, id);

        // Define the Lambda function
        Function refreshLambdaFunction = Function.Builder.create(this, Utils.getResourceName(REFRESH_LAMBDA))
                .runtime(Runtime.JAVA_17)
                .code(Code.fromAsset("src/main/java/com/aerotrack/infrastructure/lambda", AssetOptions.builder()
                        .bundling(Utils.getLambdaBuilderOptions()
                                .command(Utils.getLambdaPackagingInstructions(REFRESH_LAMBDA))
                                .build())
                        .build()))
                .handler("lambda.RefreshRequestHandler::handleRequest")
                .build();

        // Define the EventBridge rule that triggers the Lambda function
        Rule refreshEventRule = Rule.Builder.create(this, Utils.getResourceName(REFRESH_EVENT_RULE))
                .schedule(Schedule.rate(Duration.seconds(REFRESH_EVENT_RATE_SECONDS)))
                .targets(List.of(new LambdaFunction(refreshLambdaFunction)))
                .build();

    }
}
