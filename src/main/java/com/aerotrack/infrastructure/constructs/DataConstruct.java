package com.aerotrack.infrastructure.constructs;

import com.aerotrack.utils.Utils;
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
import software.amazon.jsii.JsiiObjectRef;
import software.constructs.Construct;

import java.util.List;

import static com.aerotrack.model.Constant.AEROTRACK_BUCKET;
import static com.aerotrack.model.Constant.AIRPORTS_DEPLOYMENT;
import static com.aerotrack.model.Constant.FLIGHTS_TABLE;

public class DataConstruct extends Construct {
    protected DataConstruct(JsiiObjectRef objRef) {
        super(objRef);
    }

    protected DataConstruct(InitializationMode initializationMode) {
        super(initializationMode);
    }

    public DataConstruct(@NotNull Construct scope, @NotNull String id) {
        super(scope, id);

        Table flightsTable = Table.Builder.create(this, Utils.getResourceName(FLIGHTS_TABLE))
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

        Bucket bucket = Bucket.Builder.create(this, Utils.getResourceName(AEROTRACK_BUCKET))
                .objectOwnership(ObjectOwnership.BUCKET_OWNER_ENFORCED)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .encryption(BucketEncryption.S3_MANAGED)
                .versioned(true)
                .build();

        BucketDeployment airportsDeployment = BucketDeployment.Builder.create(this, Utils.getResourceName(AIRPORTS_DEPLOYMENT))
                .sources(List.of(Source.asset("src/main/java/com/aerotrack/s3resources/")))
                .destinationBucket(bucket)
                .build();
    }
}
