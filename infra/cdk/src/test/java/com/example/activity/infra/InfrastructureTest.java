package com.example.activity.infra;

import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awscdk.*;
import software.amazon.awscdk.assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class InfrastructureTest {
    @TempDir Path out;
    private StackProps props() { return StackProps.builder().env(Environment.builder().account("111111111111").region("us-east-1").build()).build(); }
    @Test void applicationSynthesizesWithCorrectQueuesAndPrivateRuntime() {
        var app = new App(AppProps.builder().outdir(out.toString()).build());
        var stack = new ActivityStack(app, "TestActivity", props(), new ActivityStack.Settings("activities.example.com", "Z123456", "example.com",
            "arn:aws:secretsmanager:us-east-1:111111111111:secret:activity-providers-abcdef", Path.of("../..")));
        var template = Template.fromStack(stack);
        var queues = template.findResources("AWS::SQS::Queue");
        assertEquals(4, queues.size());
        queues.forEach((id, resource) -> {
            var p = (Map<?, ?>) resource.get("Properties");
            assertEquals(id.startsWith("Long"), Boolean.TRUE.equals(p.get("FifoQueue")));
            assertEquals(true, p.get("SqsManagedSseEnabled"));
            if (p.get("RedrivePolicy") instanceof Map<?, ?> redrive) {
                var arn = (Map<?, ?>) redrive.get("deadLetterTargetArn");
                var deadId = ((List<?>) arn.get("Fn::GetAtt")).getFirst();
                var dead = (Map<?, ?>) queues.get(deadId).get("Properties");
                assertEquals(Boolean.TRUE.equals(p.get("FifoQueue")), Boolean.TRUE.equals(dead.get("FifoQueue")));
            }
        });
        template.hasResourceProperties("AWS::RDS::DBInstance", Map.of("PubliclyAccessible", false, "StorageEncrypted", true, "DeletionProtection", true, "BackupRetentionPeriod", 7));
        template.hasResourceProperties("AWS::ECS::Service", Map.of(
            "DeploymentConfiguration", Match.objectLike(Map.of("DeploymentCircuitBreaker", Map.of("Enable", true, "Rollback", true))),
            "NetworkConfiguration", Map.of("AwsvpcConfiguration", Match.objectLike(Map.of("AssignPublicIp", "DISABLED")))));
        template.hasResourceProperties("AWS::ElasticLoadBalancingV2::Listener", Map.of("Port", 443, "Protocol", "HTTPS"));
        template.hasResourceProperties("AWS::ElasticLoadBalancingV2::TargetGroup", Map.of("HealthCheckPath", "/actuator/health"));
        template.hasResourceProperties("AWS::Cognito::UserPoolClient", Map.of("GenerateSecret", true, "CallbackURLs", List.of("https://activities.example.com/login/oauth2/code/cognito")));
        template.hasResourceProperties("AWS::ECS::TaskDefinition", Map.of("ContainerDefinitions", Match.arrayWith(List.of(
            Match.objectLike(Map.of("Secrets", Match.arrayWith(List.of(Match.objectLike(Map.of("Name", "FIRECRAWL_API_KEY")),
                Match.objectLike(Map.of("Name", "DATABASE_PASSWORD")), Match.objectLike(Map.of("Name", "COGNITO_CLIENT_SECRET"))))))))));
        app.synth();
    }
    @Test void identityTrustIsRestrictedToRepositoryProductionEnvironment() {
        var app = new App(AppProps.builder().outdir(out.toString()).build());
        var stack = new DeploymentIdentityStack(app, "ActivityDeploymentIdentity", props(), "example/activity", null);
        var template = Template.fromStack(stack);
        template.hasResourceProperties("AWS::IAM::Role", Map.of("AssumeRolePolicyDocument", Match.objectLike(Map.of("Statement", Match.arrayWith(List.of(
            Match.objectLike(Map.of("Action", "sts:AssumeRoleWithWebIdentity", "Condition", Map.of("StringEquals", Map.of(
                "token.actions.githubusercontent.com:aud", "sts.amazonaws.com", "token.actions.githubusercontent.com:sub", "repo:example/activity:environment:production"))))))))));
        app.synth();
    }
    @Test void identityCanReuseExistingProvider() {
        var app = new App(AppProps.builder().outdir(out.toString()).build());
        var stack = new DeploymentIdentityStack(app, "ActivityDeploymentIdentity", props(), "example/activity",
            "arn:aws:iam::111111111111:oidc-provider/token.actions.githubusercontent.com");
        Template.fromStack(stack).resourceCountIs("AWS::IAM::OIDCProvider", 0);
        app.synth();
    }
}
