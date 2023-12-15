package com.aerotrack.common;

import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;

public class CrossAccountParameterFetcher {

    public static String fetchParameter(String roleArn, String parameterName, Region region) {
        // Assume the cross-account role
        AssumeRoleResponse assumeRoleResponse;
        try (StsClient stsClient = StsClient.create()) {
            assumeRoleResponse = stsClient.assumeRole(AssumeRoleRequest.builder()
                    .roleArn(roleArn)
                    .roleSessionName("cross-account-session")
                    .build());
        }

        // Get temporary credentials
        AwsSessionCredentials sessionCredentials = AwsSessionCredentials.create(
                assumeRoleResponse.credentials().accessKeyId(),
                assumeRoleResponse.credentials().secretAccessKey(),
                assumeRoleResponse.credentials().sessionToken());

        // Create an SSM client with the assumed role's credentials
        String parameterValue;
        try (SsmClient ssmClient = SsmClient.builder()
                .credentialsProvider(StaticCredentialsProvider.create(sessionCredentials))
                .region(region)
                .build()) {

            // Fetch the parameter
            parameterValue = ssmClient.getParameter(GetParameterRequest.builder()
                    .name(parameterName)
                    .build()).parameter().value();
        }

        return parameterValue;
    }
}
