package com.aerotrack;

import com.aerotrack.infrastructure.AppStage;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.pipelines.*;
import software.amazon.awscdk.StageProps;
import software.amazon.awscdk.services.codebuild.BuildEnvironment;
import software.amazon.awscdk.services.codebuild.BuildEnvironmentVariable;
import software.amazon.awscdk.services.codebuild.BuildSpec;
import software.constructs.Construct;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;


public class PipelineStack extends Stack {

    public PipelineStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        Map<String, Object> buildSpecMap;

        try {
            String buildSpecJson = Files.readString(Path.of("buildspec.json"));
            ObjectMapper objectMapper = new ObjectMapper();
            buildSpecMap = objectMapper.readValue(buildSpecJson, Map.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read build specification", e);
        }

        CodeBuildOptions buildOptions = CodeBuildOptions.builder()
                .partialBuildSpec(BuildSpec.fromObject(buildSpecMap))
                .buildEnvironment(BuildEnvironment.builder()
                        .environmentVariables(Map.of(
                                "GITHUB_USERNAME", BuildEnvironmentVariable.builder().value("trjohnny").build(),
                                "GITHUB_TOKEN", BuildEnvironmentVariable.builder().value("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").build()))
                        .build())
                .build();

        CodePipeline pipeline = CodePipeline.Builder.create(this, "AerotrackPipeline")
                .pipelineName("AerotrackPipeline")
                .codeBuildDefaults(buildOptions)
                .crossAccountKeys(true)
                .synth(ShellStep.Builder.create("Synth")
                .input(CodePipelineSource.gitHub("trjohnny/AerotrackInfrastructure", "mainline"))
                .commands(Arrays.asList("npm install -g aws-cdk", "cdk synth"))
                .build())
            .build();

        StageDeployment alphaStage = pipeline.addStage(new AppStage(this, "Alpha", StageProps.builder()
                .env(Environment.builder()
                        .account("073873382417")
                        .region("eu-west-1")
                        .build())
                .build()));

        alphaStage.addPost(new ManualApprovalStep("approval"));

        pipeline.addStage(new AppStage(this, "Prod", StageProps.builder()
                .env(Environment.builder()
                        .account("715311622639")
                        .region("eu-west-1")
                        .build())
                .build()));
    }
}
