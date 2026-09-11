package com.example.activity.infra;

import java.nio.file.Path;
import software.amazon.awscdk.*;

public final class InfrastructureApp {
    public static void main(String[] args) {
        var app = new App();
        var props = StackProps.builder().env(Environment.builder().account(System.getenv("CDK_DEFAULT_ACCOUNT"))
            .region(System.getenv("CDK_DEFAULT_REGION")).build()).build();
        if ("identity".equals(app.getNode().tryGetContext("deployment"))) {
            new DeploymentIdentityStack(app, "ActivityDeploymentIdentity", props, required("GITHUB_REPOSITORY"), System.getenv("GITHUB_OIDC_PROVIDER_ARN"));
        } else {
            new ActivityStack(app, "ActivityService", props, new ActivityStack.Settings(required("APP_DOMAIN_NAME"),
                required("HOSTED_ZONE_ID"), required("HOSTED_ZONE_NAME"), required("PROVIDER_SECRET_ARN"), Path.of("../..")));
        }
        app.synth();
    }
    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Set " + name + " (see infra/cdk/README.md)");
        return value;
    }
}
