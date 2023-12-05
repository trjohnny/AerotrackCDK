package com.aerotrack.infrastructure.constructs;

import com.aerotrack.utils.Utils;
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
import software.constructs.Construct;

import java.util.List;

import static com.aerotrack.utils.Constant.AEROTRACK_BUCKET;
import static com.aerotrack.utils.Constant.AIRPORTS_DEPLOYMENT;
import static com.aerotrack.utils.Constant.FLIGHTS_TABLE;

@Getter
public class DataConstruct extends Construct {
    private final Bucket directionBucket;
    private final Table flightsTable;
    public DataConstruct(@NotNull Construct scope, @NotNull String id) {
        super(scope, id);

        this.flightsTable = Table.Builder.create(this, Utils.getResourceName(FLIGHTS_TABLE))
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
                .build();

        this.directionBucket = Bucket.Builder.create(this, Utils.getResourceName(AEROTRACK_BUCKET))
                .objectOwnership(ObjectOwnership.BUCKET_OWNER_ENFORCED)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .encryption(BucketEncryption.S3_MANAGED)
                .versioned(true)
                .build();

        BucketDeployment.Builder.create(this, Utils.getResourceName(AIRPORTS_DEPLOYMENT))
                .sources(List.of(Source.asset("src/main/java/com/aerotrack/infrastructure/s3data/")))
                .destinationBucket(this.directionBucket)
                .build();
    }

}
