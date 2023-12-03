package com.aerotrack.infrastructure;

import com.aerotrack.infrastructure.constructs.ApiConstruct;
import com.aerotrack.infrastructure.constructs.DataConstruct;
import com.aerotrack.infrastructure.constructs.RefreshConstruct;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.constructs.Construct;

import static com.aerotrack.model.Constant.API_CONSTRUCT;
import static com.aerotrack.model.Constant.DATA_CONSTRUCT;
import static com.aerotrack.model.Constant.REFRESH_CONSTRUCT;
public class InfraStack extends Stack {
    public InfraStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public InfraStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        new RefreshConstruct(this, REFRESH_CONSTRUCT);

        new ApiConstruct(this, API_CONSTRUCT);

        new DataConstruct(this, DATA_CONSTRUCT);
    }

}