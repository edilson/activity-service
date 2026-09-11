package com.example.activity.infra;

import java.util.*;
import software.amazon.awscdk.*;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.iam.*;
import software.constructs.Construct;

public class DeploymentIdentityStack extends Stack {
    public DeploymentIdentityStack(Construct scope, String id, StackProps props, String repository, String providerArn) {
        super(scope, id, props);
        if (repository == null || !repository.matches("[\\w.-]+/[\\w.-]+"))
            throw new IllegalArgumentException("Set GITHUB_REPOSITORY=owner/repository");
        var provider = providerArn != null && !providerArn.isBlank()
            ? OpenIdConnectProvider.fromOpenIdConnectProviderArn(this, "GitHub", providerArn)
            : OpenIdConnectProvider.Builder.create(this, "GitHub").url("https://token.actions.githubusercontent.com").clientIds(List.of("sts.amazonaws.com")).build();
        var role = Role.Builder.create(this, "DeploymentRole").assumedBy(new FederatedPrincipal(provider.getOpenIdConnectProviderArn(),
            Map.of("StringEquals", Map.of("token.actions.githubusercontent.com:aud", "sts.amazonaws.com",
                "token.actions.githubusercontent.com:sub", "repo:" + repository + ":environment:production")), "sts:AssumeRoleWithWebIdentity")).build();
        role.addToPolicy(PolicyStatement.Builder.create().actions(List.of("sts:AssumeRole")).resources(List.of(formatArn(ArnComponents.builder()
            .service("iam").region("").resource("role").resourceName("cdk-hnb659fds-*-role-" + getAccount() + "-" + getRegion()).build()))).build());
        role.addToPolicy(PolicyStatement.Builder.create().actions(List.of("cloudformation:DescribeStacks")).resources(List.of(formatArn(ArnComponents.builder()
            .service("cloudformation").resource("stack").resourceName("CDKToolkit/*").build()))).build());
        role.addToPolicy(PolicyStatement.Builder.create().actions(List.of("ssm:GetParameter")).resources(List.of(formatArn(ArnComponents.builder()
            .service("ssm").resource("parameter").resourceName("cdk-bootstrap/hnb659fds/version").build()))).build());
        CfnOutput.Builder.create(this, "DeployRoleArn").value(role.getRoleArn()).build();
    }
}
