package com.myorg;

import com.myorg.infrastructure.AppStage;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.pipelines.*;
import software.amazon.awscdk.StageProps;
import software.constructs.Construct;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;

import java.util.Arrays;


public class PipelineStack extends Stack {

    public PipelineStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        CodePipeline pipeline = CodePipeline.Builder.create(this, "AerotrackPipeline")
            .pipelineName("AerotrackPipeline")
                .crossAccountKeys(true)
                .synth(ShellStep.Builder.create("Synth")
                .input(CodePipelineSource.gitHub("trjohnny/aerotrack", "mainline"))
                .commands(Arrays.asList("npm install -g aws-cdk", "cdk synth"))
                .build())
            .build();

        StageDeployment alphaStage = pipeline.addStage(new AppStage(this, "AlphaStage", StageProps.builder()
                .env(Environment.builder()
                        .account("073873382417")
                        .region("eu-west-1")
                        .build())
                .build()));

        alphaStage.addPost(new ManualApprovalStep("approval"));

        pipeline.addStage(new AppStage(this, "ProdStage", StageProps.builder()
                .env(Environment.builder()
                        .account("715311622639")
                        .region("eu-west-1")
                        .build())
                .build()));
    }
}
