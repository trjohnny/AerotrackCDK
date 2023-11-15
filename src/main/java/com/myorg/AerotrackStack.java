package com.myorg;

import software.amazon.awscdk.pipelines.CodePipeline;
import software.amazon.awscdk.pipelines.CodePipelineSource;
import software.amazon.awscdk.pipelines.ShellStep;
import software.constructs.Construct;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;

import java.util.Arrays;
// import software.amazon.awscdk.Duration;
// import software.amazon.awscdk.services.sqs.Queue;

public class AerotrackStack extends Stack {
    public AerotrackStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public AerotrackStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // The code that defines your stack goes here

        // example resource
        // final Queue queue = Queue.Builder.create(this, "AerotrackQueue")
        //         .visibilityTimeout(Duration.seconds(300))
        //         .build();

        CodePipeline pipeline = CodePipeline.Builder.create(this, "pipeline")
                .pipelineName("ATPipeline")
                .synth(ShellStep.Builder.create("Synth")
                        .input(CodePipelineSource.gitHub("trjohnny/aerotrack", "mainline"))
                        .commands(Arrays.asList("npm install -g aws-cdk", "cdk synth"))
                        .build())
                .build();
    }
}
