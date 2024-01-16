package com.aerotrack.infrastructure.constructs;

import com.aerotrack.common.InfraUtils;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.BillingMode;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketEncryption;
import software.amazon.awscdk.services.s3.ObjectOwnership;
import software.amazon.awscdk.services.s3.deployment.BucketDeployment;
import software.amazon.awscdk.services.s3.deployment.Source;
import software.amazon.awscdk.services.ssm.StringParameter;
import software.constructs.Construct;

import java.util.List;


@Getter
public class DataConstruct extends Construct {
    private final Bucket airportsBucket;
    private final Table flightsTable;
    public DataConstruct(@NotNull Construct scope, @NotNull String id) {
        super(scope, id);

        this.flightsTable = Table.Builder.create(this, "FlightsTable")
                .partitionKey(Attribute.builder()
                        .name("direction")
                        .type(AttributeType.STRING)
                        .build())
                .sortKey(Attribute.builder()
                        .name("departureDateTime")
                        .type(AttributeType.STRING)
                        .build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .deletionProtection(false)
                .timeToLiveAttribute("TTL")
                .build();

        StringParameter.Builder.create(this, InfraUtils.getResourceName("FlightsTableNameParameter"))
                .parameterName(InfraUtils.getResourceName("FlightsTable"))
                .stringValue(flightsTable.getTableName())
                .build();

        this.airportsBucket = Bucket.Builder.create(this, InfraUtils.getResourceName("AerotrackBucket"))
                .objectOwnership(ObjectOwnership.BUCKET_OWNER_ENFORCED)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .encryption(BucketEncryption.S3_MANAGED)
                .versioned(true)
                .build();

        BucketDeployment.Builder.create(this, InfraUtils.getResourceName("AirportsDeployment"))
                .sources(List.of(Source.asset("src/main/java/com/aerotrack/infrastructure/s3data/")))
                .destinationBucket(this.airportsBucket)
                .build();
    }

}
