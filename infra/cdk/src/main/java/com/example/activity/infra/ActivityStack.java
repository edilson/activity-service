package com.example.activity.infra;

import java.nio.file.Path;
import java.util.*;
import software.amazon.awscdk.*;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.patterns.*;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationProtocol;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.rds.*;
import software.amazon.awscdk.services.s3.*;
import software.amazon.awscdk.services.sqs.*;
import software.amazon.awscdk.services.sqs.Queue;
import software.amazon.awscdk.services.cognito.*;
import software.amazon.awscdk.services.route53.*;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.constructs.Construct;

public class ActivityStack extends Stack {
    public record Settings(String domainName, String hostedZoneId, String hostedZoneName, String providerSecretArn, Path sourceRoot) {}

    public ActivityStack(Construct scope, String id, StackProps props, Settings settings) {
        super(scope, id, props);
        String origin = "https://" + settings.domainName();
        var vpc = Vpc.Builder.create(this, "Vpc").maxAzs(2).natGateways(1).subnetConfiguration(List.of(
            SubnetConfiguration.builder().name("Public").subnetType(SubnetType.PUBLIC).build(),
            SubnetConfiguration.builder().name("Application").subnetType(SubnetType.PRIVATE_WITH_EGRESS).build(),
            SubnetConfiguration.builder().name("Database").subnetType(SubnetType.PRIVATE_ISOLATED).build())).build();
        var files = Bucket.Builder.create(this, "Files").encryption(BucketEncryption.S3_MANAGED)
            .blockPublicAccess(BlockPublicAccess.BLOCK_ALL).enforceSsl(true).versioned(true).removalPolicy(RemovalPolicy.RETAIN).build();
        var shortQueue = queue("ShortRides", false);
        var longQueue = queue("LongRides", true);
        var database = DatabaseInstance.Builder.create(this, "Database").vpc(vpc)
            .vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PRIVATE_ISOLATED).build())
            .engine(DatabaseInstanceEngine.postgres(PostgresInstanceEngineProps.builder().version(PostgresEngineVersion.VER_17).build()))
            .instanceType(software.amazon.awscdk.services.ec2.InstanceType.of(InstanceClass.T4G, InstanceSize.MICRO))
            .credentials(Credentials.fromGeneratedSecret("activity")).databaseName("activities")
            .allocatedStorage(20).maxAllocatedStorage(100).storageEncrypted(true).publiclyAccessible(false)
            .backupRetention(Duration.days(7)).deletionProtection(true).removalPolicy(RemovalPolicy.SNAPSHOT).multiAz(false).build();
        var pool = UserPool.Builder.create(this, "Users").selfSignUpEnabled(false)
            .signInAliases(SignInAliases.builder().email(true).build()).removalPolicy(RemovalPolicy.RETAIN).build();
        var client = pool.addClient("WebClient", UserPoolClientOptions.builder().generateSecret(true)
            .oAuth(OAuthSettings.builder().flows(OAuthFlows.builder().authorizationCodeGrant(true).build())
                .scopes(List.of(OAuthScope.OPENID, OAuthScope.EMAIL, OAuthScope.PROFILE))
                .callbackUrls(List.of(origin + "/login/oauth2/code/cognito")).logoutUrls(List.of(origin)).build()).build());
        var domain = pool.addDomain("HostedDomain", UserPoolDomainOptions.builder()
            .cognitoDomain(CognitoDomainOptions.builder().domainPrefix("activity-" + getAccount() + "-" + getRegion()).build()).build());
        var clientSecret = software.amazon.awscdk.services.secretsmanager.Secret.Builder.create(this, "CognitoClientSecret")
            .secretStringValue(client.getUserPoolClientSecret()).build();
        var providers = software.amazon.awscdk.services.secretsmanager.Secret.fromSecretCompleteArn(this, "Providers", settings.providerSecretArn());
        var zone = HostedZone.fromHostedZoneAttributes(this, "Zone", HostedZoneAttributes.builder()
            .hostedZoneId(settings.hostedZoneId()).zoneName(settings.hostedZoneName()).build());
        Map<String, Secret> secrets = new LinkedHashMap<>();
        for (String key : List.of("FIRECRAWL_API_KEY", "GROQ_API_KEY", "OAUTH_ENCRYPTION_KEY", "POLAR_CLIENT_ID", "POLAR_CLIENT_SECRET", "GARMIN_CLIENT_ID", "GARMIN_CLIENT_SECRET"))
            secrets.put(key, Secret.fromSecretsManager(providers, key));
        secrets.put("DATABASE_PASSWORD", Secret.fromSecretsManager(database.getSecret(), "password"));
        secrets.put("COGNITO_CLIENT_SECRET", Secret.fromSecretsManager(clientSecret));
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("DATABASE_URL", "jdbc:postgresql://" + database.getDbInstanceEndpointAddress() + ":5432/activities?sslmode=require");
        environment.put("DATABASE_USERNAME", "activity"); environment.put("AUTH_MODE", "cognito"); environment.put("COOKIE_SECURE", "true");
        environment.put("SERVER_FORWARD_HEADERS_STRATEGY", "framework"); environment.put("PUBLIC_BASE_URL", origin);
        environment.put("COGNITO_ISSUER", pool.getUserPoolProviderUrl()); environment.put("COGNITO_CLIENT_ID", client.getUserPoolClientId());
        environment.put("COGNITO_DOMAIN", domain.baseUrl()); environment.put("COGNITO_REDIRECT_URI", origin + "/login/oauth2/code/cognito");
        environment.put("SQS_ENABLED", "true"); environment.put("AWS_REGION", getRegion()); environment.put("S3_BUCKET", files.getBucketName());
        environment.put("SQS_SHORT_QUEUE_URL", shortQueue.getQueueUrl()); environment.put("SQS_LONG_QUEUE_URL", longQueue.getQueueUrl());
        var service = ApplicationLoadBalancedFargateService.Builder.create(this, "Application")
            .vpc(vpc).cpu(512).memoryLimitMiB(1024).desiredCount(1).minHealthyPercent(100).maxHealthyPercent(200)
            .taskSubnets(SubnetSelection.builder().subnetType(SubnetType.PRIVATE_WITH_EGRESS).build()).assignPublicIp(false)
            .domainName(settings.domainName()).domainZone(zone).redirectHttp(true).protocol(ApplicationProtocol.HTTPS)
            .circuitBreaker(DeploymentCircuitBreaker.builder().rollback(true).build()).healthCheckGracePeriod(Duration.minutes(3)).idleTimeout(Duration.seconds(120))
            .taskImageOptions(ApplicationLoadBalancedTaskImageOptions.builder()
                .image(ContainerImage.fromAsset(settings.sourceRoot().toAbsolutePath().normalize().toString(), AssetImageProps.builder()
                    .exclude(List.of(".git", ".tools", "target", "infra/cdk/target", "infra/cdk/node_modules", "infra/cdk/cdk.out")).build()))
                .containerPort(8080).logDriver(LogDrivers.awsLogs(AwsLogDriverProps.builder().streamPrefix("activity").logRetention(RetentionDays.ONE_MONTH).build()))
                .environment(environment).secrets(secrets).build()).build();
        service.getTargetGroup().configureHealthCheck(HealthCheck.builder().path("/actuator/health").healthyHttpCodes("200").build());
        service.getTargetGroup().enableCookieStickiness(Duration.hours(1));
        database.getConnections().allowDefaultPortFrom(service.getService());
        files.grantReadWrite(service.getTaskDefinition().getTaskRole(), "activities/*");
        shortQueue.grantSendMessages(service.getTaskDefinition().getTaskRole()); longQueue.grantSendMessages(service.getTaskDefinition().getTaskRole());
        output("ApplicationUrl", origin); output("UserPoolId", pool.getUserPoolId()); output("ShortQueueUrl", shortQueue.getQueueUrl());
        output("LongQueueUrl", longQueue.getQueueUrl()); output("FilesBucket", files.getBucketName());
    }
    private Queue queue(String name, boolean fifo) {
        var dead = Queue.Builder.create(this, name + "DeadLetters").fifo(fifo).retentionPeriod(Duration.days(14))
            .encryption(QueueEncryption.SQS_MANAGED).removalPolicy(RemovalPolicy.RETAIN).build();
        var queue = Queue.Builder.create(this, name).fifo(fifo).encryption(QueueEncryption.SQS_MANAGED)
            .visibilityTimeout(Duration.seconds(120)).receiveMessageWaitTime(Duration.seconds(20))
            .deadLetterQueue(DeadLetterQueue.builder().queue(dead).maxReceiveCount(5).build()).removalPolicy(RemovalPolicy.RETAIN);
        if (fifo) queue.contentBasedDeduplication(false);
        return queue.build();
    }
    private void output(String id, String value) { CfnOutput.Builder.create(this, id).value(value).build(); }
}


