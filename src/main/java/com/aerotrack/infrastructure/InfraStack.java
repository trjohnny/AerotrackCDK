package com.aerotrack.infrastructure;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.constructs.Construct;

public class InfraStack extends Stack {
    public InfraStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public InfraStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);
    }

}