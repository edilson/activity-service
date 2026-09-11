# AWS CDK deployment

The infrastructure is a standalone Java 21 Maven project under `src/main/java/com/example/activity/infra`, with JUnit tests under `src/test/java`. `cdk.json` runs `mvn -q compile exec:java`. Node.js remains required by the CDK CLI and the Java CDK runtime bridge; npm manages only the pinned CLI, while Maven manages the infrastructure dependencies.

This is the complete application deployment. The older `infra/*.yml` files are standalone templates; do not deploy them alongside this stack expecting CDK to adopt their resources. This stack creates a new database, bucket, Cognito pool, and queues. Existing production data/users require an explicit migration or CDK resource import before switching traffic.

## One-time setup

Use Java 21, Maven 3.9+, Node 22+, Docker, and AWS credentials with infrastructure provisioning permissions. Choose the AWS account/region and an existing public Route 53 hosted zone whose DNS delegation is working. CDK creates the HTTPS certificate and application DNS record in that zone.

1. Run `npm ci` in `infra/cdk`, then `npx cdk bootstrap aws://ACCOUNT/REGION` with administrator/operator credentials. The default bootstrap CloudFormation execution role can provision IAM; restrict its policies to your organization's deployment policy as needed.
2. Create a Secrets Manager JSON secret in that region containing every key below. Use empty strings for unused Polar/Garmin integrations. Generate `OAUTH_ENCRYPTION_KEY` as 32 random bytes encoded in base64; preserve it across deployments. Do not put secret values in GitHub variables or CDK context.

```json
{
  "FIRECRAWL_API_KEY": "...",
  "GROQ_API_KEY": "...",
  "OAUTH_ENCRYPTION_KEY": "...",
  "POLAR_CLIENT_ID": "",
  "POLAR_CLIENT_SECRET": "",
  "GARMIN_CLIENT_ID": "",
  "GARMIN_CLIENT_SECRET": ""
}
```

3. Set `GITHUB_REPOSITORY=owner/repository`. If the account already has the GitHub OIDC provider, also set `GITHUB_OIDC_PROVIDER_ARN` to its ARN. Run `npx cdk deploy -c deployment=identity ActivityDeploymentIdentity` using operator credentials. This separate CDK stack creates an OIDC role trusted only by this repository's `production` environment, with access to the default CDK bootstrap roles. Save its `DeployRoleArn` output.
4. Create GitHub environment `production` and restrict deployment branches to `main`. Add the following environment variables:

| Variable | Value |
| --- | --- |
| `AWS_DEPLOY_ROLE_ARN` | Identity stack output |
| `AWS_REGION` | Target AWS region |
| `APP_DOMAIN_NAME` | e.g. `activities.example.com` |
| `HOSTED_ZONE_ID` | Existing public Route 53 zone ID |
| `HOSTED_ZONE_NAME` | e.g. `example.com` |
| `PROVIDER_SECRET_ARN` | Complete Secrets Manager ARN, including its generated suffix |

5. Push to `main` or run **Test and deploy** manually on `main`. Pull requests run only the quality gate. Configure the `quality-gate` check as required in branch protection to prevent merging failed tests. Deployment uses GitHub OIDC, builds/publishes the Docker asset through CDK, and waits for CloudFormation/ECS stabilization before checking HTTPS health.

For local synthesis/deployment, export the four application configuration variables from the table plus AWS region/credentials and run `npm run synth` or `npm run deploy`. `mvn verify` runs JUnit infrastructure assertions and synthesizes both stacks with fake configuration; it needs no AWS credentials or Docker. From the repository root, use `./mvnw -f infra/cdk/pom.xml verify` (Windows: `mvnw.cmd`).

## Runtime and operations

The stack creates public HTTPS ALB, private Fargate tasks, private PostgreSQL 17 with encrypted storage and seven-day backups, NAT egress for external APIs, encrypted/versioned private S3, standard short-activity queues and FIFO long-activity queues (including matching DLQs), Cognito, Secrets Manager injection, and CloudWatch logs. Task permissions cover activity objects and sending to the two queues. Health checks use `/actuator/health`; Flyway migrations run at application startup. ECS rolls back failed deployments; database migrations must remain backward compatible because ECS rollback does not undo SQL migrations.

The baseline is one Fargate task and a single-AZ database, with one NAT gateway. These incur ongoing AWS charges and are not a high-availability configuration. ALB stickiness supports the current in-memory sessions, but deployments still end existing sessions; add shared session storage before relying on multi-instance session continuity. Cognito signup is disabled; create/invite users through your pool. Provider keys are injected when tasks start; after rotating the external secret, force a new ECS deployment. The OAuth encryption key requires a ciphertext migration if changed.

Database deletion protection is enabled and removal takes a snapshot. The file bucket, user pool and queues are retained. Stack deletion therefore does not clean up all resources. No queue consumer is deployed because this repository only publishes events.

Short activity queues cannot be converted from FIFO in place. Drain/replay pending old messages with event-ID deduplication, update producers/consumers to the new standard URL, and retire the old short FIFO queue only after reconciliation. Long activity main and dead-letter queues remain FIFO.

References: [GitHub AWS OIDC](https://docs.github.com/en/actions/how-tos/secure-your-work/security-harden-deployments/oidc-in-aws), [CDK Fargate service](https://docs.aws.amazon.com/cdk/api/v2/docs/aws-cdk-lib.aws_ecs_patterns.ApplicationLoadBalancedFargateService.html).

