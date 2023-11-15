package com.myorg;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

public class AerotrackApp {
    public static void main(final String[] args) {
        App app = new App();

        new PipelineStack(app, "PipelineStack", StackProps.builder()
                .env(Environment.builder()
                        .account("073873382417")
                        .region("eu-west-1")
                        .build())
                .build());

        app.synth();
    }
}
