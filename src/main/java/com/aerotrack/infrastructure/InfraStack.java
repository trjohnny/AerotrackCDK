package com.aerotrack.infrastructure;

import com.aerotrack.infrastructure.constructs.ApiConstruct;
import com.aerotrack.infrastructure.constructs.DataConstruct;
import com.aerotrack.infrastructure.constructs.RefreshConstruct;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.constructs.Construct;

public class InfraStack extends Stack {
    public InfraStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public InfraStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);


        DataConstruct data = new DataConstruct(this, "DataConstruct");

        new ApiConstruct(this, "ApiConstruct", data.getAirportsBucket(), data.getFlightsTable());

        new RefreshConstruct(this, "RefreshConstruct", data.getAirportsBucket(), data.getFlightsTable());
    }

}