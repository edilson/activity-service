# Cycling activity service

Java 21 / Spring Boot service for GPX, FIT, PNG/JPEG screenshots, and public Strava activity links. S3 stores original activity files and user photos. PostgreSQL stores their object paths, normalized activities, extraction responses, route coordinates, encrypted provider connections, and the transactional SQS outbox.

## Run locally with Docker Compose

1. Copy `.env.example` to `.env` and set `DATABASE_PASSWORD`.
2. For Cognito, set `COGNITO_ISSUER`, `COGNITO_CLIENT_ID`, `COGNITO_CLIENT_SECRET`, and `COGNITO_DOMAIN` as described below. Keep `AUTH_MODE=cognito`.
3. Run:

```sh
docker compose up --build -d
docker compose logs -f app
```

Compose builds the non-root application container, starts PostgreSQL 17 and LocalStack with named data volumes, creates the standard short-activity queue and FIFO long-activity queue and the `activity-files` S3 bucket, waits for dependencies, and exposes the application at http://localhost:8080. Ports are bound to localhost. The app connects to `postgres:5432` and `localstack:4566` inside the Docker network. `SQS_ENABLED=true` enables local publishing; otherwise events remain pending in PostgreSQL. File imports and photo uploads always require S3, regardless of the SQS setting.

For offline development without an AWS account, explicitly set `AUTH_MODE=local` and a strong `API_PASSWORD` in `.env`. This enables the single local HTTP Basic account (`API_USERNAME`, default `activity`). Cognito is the default authentication mode; local authentication is not enabled alongside Cognito.

`docker compose down` stops the stack and preserves PostgreSQL data. `docker compose down -v` deletes the database volume. Changing `DATABASE_PASSWORD` after initializing the volume does not change an existing database user's password automatically.

Compose reads `.env` for interpolation. Spring running outside Docker does not automatically load `.env`: export the variables into your shell. The Compose configuration is for local development, with HTTP cookies and LocalStack test credentials; production requires HTTPS, secure cookies, workload IAM credentials, and a managed database/secret store.

## Cognito authentication

Use a Cognito **User Pool**, with a confidential web app client that has a client secret. Enable the authorization-code grant and scopes `openid`, `email`, and `profile`. Configure a Cognito hosted domain. A deployable starting point is `infra/cognito.yml`; its outputs provide the pool ID, issuer, app client ID, and domain. Retrieve the generated client secret through your AWS account and store it securely (it is not exposed as a stack output).

Register this exact local callback URL:

```text
http://localhost:8080/login/oauth2/code/cognito
```

Configure:

```dotenv
AUTH_MODE=cognito
COGNITO_ISSUER=https://cognito-idp.us-east-1.amazonaws.com/us-east-1_YOURPOOL
COGNITO_CLIENT_ID=your-app-client-id
COGNITO_CLIENT_SECRET=your-app-client-secret
COGNITO_DOMAIN=https://your-domain.auth.us-east-1.amazoncognito.com
COGNITO_REDIRECT_URI=http://localhost:8080/login/oauth2/code/cognito
```

Open [Cognito sign-in](http://localhost:8080/oauth2/authorization/cognito). Spring redirects the browser to Cognito, using authorization code, state, and PKCE. After login, `/api/me` returns the authenticated user's ID. Browser authentication uses an HTTP-only session cookie. Activity and Polar/Garmin connection ownership use the stable Cognito `sub`, not an email address or a caller-supplied user ID.

API clients can also send `Authorization: Bearer <Cognito access token>`. The service validates RS256 signatures using the pool JWKS, issuer, expiration, `token_use=access`, matching `client_id`, and a nonempty subject. ID tokens are rejected as API bearer tokens. Do not put the confidential app-client secret into browser/mobile code; obtain tokens through a trusted server-side flow. JWT verification cannot immediately detect a revoked token; expiry bounds token lifetime.

Browser/session writes require CSRF protection: get `/api/csrf`, retain its session cookie, and send the returned token under the returned header name. Bearer-authenticated writes do not require a CSRF token. `/api/*` returns 401 for unauthenticated requests; browser sign-in starts explicitly at `/oauth2/authorization/cognito`. POST `/logout` with CSRF ends the local application session; it does not revoke provider tokens or sign out of Cognito's hosted-domain session.

## Stored activity data

- `activities`: distance in meters, duration in seconds, elevation gain in meters, encoded polyline, source type, owner, and import timestamp.
- `activity_sources`: S3 bucket, object key, stable `s3://bucket/key` URL, byte size, filename, detected/assigned content type, original submitted URL, full provider response (`jsonb`), and extracted route coordinates (`jsonb`). New file bytes are uploaded to S3, not stored in PostgreSQL.
- `activity_photos`: activity ID, filename, detected content type, S3 bucket/key/URL, byte size, and creation time. Each activity can have multiple photos.
- GPX and FIT originals retain all source fields, including extension/developer fields and samples that are not mapped into normalized columns. Download the original for complete reprocessing.
- Groq responses retain all returned fields and the extracted JSON text. The extraction prompt requests additional visible activity fields (heart rate, cadence, speed, power, device, date, splits, etc.). The original screenshot is also retained because OCR cannot guarantee that every visible value is extracted accurately.
- Firecrawl requests structured JSON, Markdown, and raw HTML; the SDK document is retained, including structured JSON, Markdown, raw HTML, and metadata (unknown fields discarded by the SDK are not retained). Only content Firecrawl can retrieve is available; private or inaccessible Strava data cannot be recovered.

Source references, normalized activity, and the outbox event commit in one PostgreSQL transaction after S3 accepts the file. S3 and PostgreSQL cannot share an atomic transaction: rollback triggers best-effort deletion of uploaded objects, including uploads that timed out after being accepted by S3. A process crash or failed cleanup can leave an unreferenced object; reconcile S3 keys against database references operationally. Listing activities does not return file bytes. Owned source metadata/JSON is available through `/api/activities/{id}/source`; `/api/activities/{id}/file` fetches the original from S3 through an authenticated attachment response. Neither endpoint permits access to another user's activity. Raw HTML is returned as JSON data, never rendered as an HTML page.

Flyway V3 adds names, S3 references, and photos without rewriting prior migrations. Every minute, a background worker locks up to ten legacy source rows, uploads their database bytes to S3, and clears those bytes only in the transaction that stores the S3 reference. Failed batches retain the bytes and retry on the next pass. Legacy downloads remain available during migration. The nullable `original_file` column is retained solely for this migration; new uploads never populate it. Imports predating V2 cannot have discarded files reconstructed and need reimporting. The previous H2 runtime database is not automatically migrated into PostgreSQL. Existing local-account activities require an explicit owner mapping when moving to Cognito subjects.

## S3, activity names, and photos

Outside Compose, configure `S3_BUCKET` and `AWS_REGION`; production uses the AWS default credential chain. Set `S3_ENDPOINT=http://localhost:4566` only for local development. `infra/s3.yml` provisions a private, encrypted bucket with public access blocked and TLS enforced. Grant the application role `s3:PutObject`, `s3:GetObject`, and `s3:DeleteObject` on `arn:aws:s3:::YOUR_BUCKET/activities/*`. Delete permission supports rollback cleanup. The application does not create a production bucket automatically.

Objects use generated keys under `activities/{activityId}/source/{uuid}` and `activities/{activityId}/photos/{uuid}`; user filenames never determine an S3 key. Stored URLs are durable S3 locators, not public URLs or expiring signed URLs. Use the authenticated download endpoints to retrieve private content. Uploads request AES-256 server-side encryption. Preserve both the database and bucket when backing up or restoring the service.

Activity `name` is optional, limited to 255 characters, and trimmed; blank or null clears it. Supply it as a multipart `name` field on file import or a JSON `name` field on Strava import. Update it later with `PATCH /api/activities/{id}/name` and `{"name":"Sunday ride"}` (or `{"name":null}` to clear).

Add photos after importing an activity with `POST /api/activities/{id}/photos`, multipart field `file`. Upload one photo per request and repeat for multiple photos. Photos must be readable PNG or JPEG, at most 10 MiB and 20 megapixels. Their actual content is validated independently of their filename. Photos are attachments and do not change the activity's metrics. List metadata and S3 paths with `GET /api/activities/{id}/photos?page=0` and download with `GET /api/activities/{id}/photos/{photoId}/file`. Each operation checks activity ownership; a photo ID must also belong to that activity. Existing Cognito/CSRF rules apply.

```sh
curl -H "Authorization: Bearer $ACCESS_TOKEN" -F 'name=Sunday ride' -F 'file=@ride.gpx' http://localhost:8080/api/activities/files
curl -H "Authorization: Bearer $ACCESS_TOKEN" -F 'file=@photo.jpg' http://localhost:8080/api/activities/ACTIVITY_ID/photos
curl -X PATCH -H "Authorization: Bearer $ACCESS_TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Morning ride"}' http://localhost:8080/api/activities/ACTIVITY_ID/name
```

## API

| Method | Endpoint | Purpose |
| --- | --- | --- |
| GET | `/api/me` | Current user ID |
| GET | `/api/csrf` | Session CSRF token |
| POST | `/api/activities/files` | Multipart `file`: GPX, FIT, PNG, JPG, JPEG |
| POST | `/api/activities/strava` | JSON with a full Strava activity URL |
| GET | `/api/activities/{id}` | Owned normalized activity |
| GET | `/api/activities/{id}/source` | Owned source metadata, extraction JSON, and coordinates |
| GET | `/api/activities/{id}/file` | Original file attachment |
| PATCH | `/api/activities/{id}/name` | Set or clear the optional name |
| POST | `/api/activities/{id}/photos` | Add a PNG/JPEG photo |
| GET | `/api/activities/{id}/photos` | List owned photo metadata and S3 paths |
| GET | `/api/activities/{id}/photos/{photoId}/file` | Download an owned photo |
| GET | `/api/activities?page=0` | Owned activities, 20 per page |
| GET | `/oauth2/authorization/cognito` | Cognito browser sign-in |
| GET | `/oauth/{polar,garmin}/authorize` | Connect a provider after sign-in |
| GET | `/oauth/{provider}/callback` | Provider callback |
| POST | `/oauth/garmin/refresh` | Rotate connected Garmin token |
| GET | `/actuator/health` | Health check |

Example with a Cognito access token:

```sh
curl -H "Authorization: Bearer $ACCESS_TOKEN" -F 'file=@ride.gpx' http://localhost:8080/api/activities/files
curl -H "Authorization: Bearer $ACCESS_TOKEN" -H 'Content-Type: application/json' \
  -d '{"url":"https://www.strava.com/activities/123"}' http://localhost:8080/api/activities/strava
```

For local Basic authentication, retain a session and CSRF token. PowerShell 7 example:

```powershell
$pair = 'activity:' + $env:API_PASSWORD
$headers = @{ Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($pair)) }
$csrf = Invoke-RestMethod http://localhost:8080/api/csrf -Headers $headers -SessionVariable session
$headers[$csrf.headerName] = $csrf.token
Invoke-RestMethod http://localhost:8080/api/activities/files -Method Post `
  -Headers $headers -WebSession $session -Form @{file=Get-Item './ride.gpx'}
```

Successful imports return `201 Created` and `Location`. Missing/invalid mandatory metrics return `422`, malformed requests `400`, provider failures `502`, and oversized uploads `413`. Failed imports are rejected without creating a partial activity. Reimporting the same file creates another activity; request-level deduplication is not implemented.

## Metric and route rules

Distance and duration must be finite and positive. Altimetry means total positive elevation gain in meters and must be finite and nonnegative. Missing values are never replaced with zero. FIT uses timer time; GPX sums timestamp differences within track segments; screenshots/Strava use explicitly shown moving/timer time, otherwise elapsed duration.

GPX requires coordinates, elevations, and timestamps. Distance uses Haversine and ascent sums positive elevation changes without smoothing. Valid disconnected segments contribute metrics, but their single normalized polyline is omitted to avoid invented connecting legs; all original segments remain in the stored GPX. GPX is treated as cycling by caller intent because its format does not require a sport identifier.

FIT uses Garmin's official SDK with CRC/integrity checks. Exactly one cycling session with distance, timer time, and total ascent is required. Optional GPS records become route coordinates and a precision-5 Google encoded polyline. Screenshots do not normally contain exact geographic coordinates, so no route is guessed from map pixels.

Activity uploads are limited to 20 MiB, extraction screenshots to 4 MiB with PNG/JPEG signature checks, and extracted routes to 100000 points. Original file bytes are retained in S3. Additional photos have the separate limits described above.

## Firecrawl, Groq, and provider connections

Set `FIRECRAWL_API_KEY` and `GROQ_API_KEY`. Firecrawl uses the official `com.firecrawl:firecrawl-java:1.12.1` SDK and its single-page `scrape` operation (`/v2/scrape`). SDK retries and connection retries are disabled. Submit full HTTPS `strava.com/activities/{id}` URLs; expand short/share links first. URL query parameters are removed from the scraping request, while the original submitted URL is retained in owned source storage. Upload GPX/FIT for private or login-only rides.

Groq uses base64 image input and JSON mode at `/openai/v1/chat/completions`. `GROQ_VISION_MODEL` defaults to `qwen/qwen3.6-27b`. Outbound connect/read deadlines are 10/90 seconds; failed paid extractions are not retried automatically.

For Polar/Garmin, set their client IDs/secrets, `PUBLIC_BASE_URL`, and a base64-encoded random 32-byte `OAUTH_ENCRYPTION_KEY`. Register `/oauth/polar/callback` and `/oauth/garmin/callback` at your public origin. Keep the encryption key across restarts; stored tokens use AES-256-GCM, with owner/provider authenticated context. Changing the key requires migrating ciphertext.

Provider state is bound to the browser session, expires in ten minutes, and is consumed once. Garmin uses PKCE, user-ID lookup, and an explicit refresh endpoint with row locking. Polar uses Basic client authentication and registers the user with AccessLink. These connections do not automatically import provider history or subscribe to webhooks. Garmin requires developer-program approval and Polar requires an AccessLink client and user consents. Pending sessions are in memory; multiple instances need sticky sessions or shared session storage.

## SQS queues

The local stack initializes `activities-short` and `activities-long.fifo`. Enable publishing with `SQS_ENABLED=true`. For AWS, `infra/sqs.yml` provisions an encrypted standard short queue and FIFO long queue with matching dead-letter queue types and redrive after five failed receives. Supply the resulting URLs, region, and workload credentials; grant `sqs:SendMessage` on both ARNs.

- Short queue: distance **< 100000 meters**.
- Long queue: distance **>= 100000 meters**, including exactly 100 km.
- Long queue only: message group is the stable owner hash; deduplication ID is the persistent event UUID. Short queue sends omit both FIFO fields.

The dispatcher locks up to 20 due outbox rows every five seconds. Failed sends back off up to one hour. Disabled SQS leaves events pending. Messages contain an activity reference and distance, avoiding large route payloads. Downstream consumers must deduplicate persistent event IDs because a crash after SQS acceptance can cause later redelivery beyond the five-minute SQS deduplication window. The standard short queue provides at-least-once delivery without ordering. The long FIFO queue preserves SQS acceptance order within a group, not original import order across retries or separate queues. Published outbox rows are retained; configure retention and pending-age monitoring for your deployment.

## Build and tests

Install Java 21. The Maven wrapper downloads Maven on first use:

```sh
./mvnw verify
# With Docker available, also run the persistence contract against PostgreSQL 17:
./mvnw -Ppostgres-it verify
```

Windows uses `mvnw.cmd`. Standard verification runs unit tests, signed-JWT validation, mocked HTTP/MVC security tests, and H2 persistence tests in PostgreSQL compatibility mode. H2 is **test-scope only** and is not packaged in the application. The opt-in PostgreSQL integration tests use Testcontainers and require Docker. Coverage is in `target/site/jacoco/index.html`. The application image build also runs standard verification.

To run outside Docker, start PostgreSQL, export `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` and authentication settings, then run `./mvnw spring-boot:run`. There is no H2 runtime fallback.

## References

- [Cognito JWT verification](https://docs.aws.amazon.com/cognito/latest/developerguide/amazon-cognito-user-pools-using-tokens-verifying-a-jwt.html)
- [Cognito authorization-code token flow](https://docs.aws.amazon.com/cognito/latest/developerguide/token-endpoint.html)
- [Spring Security JWT resource server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [Firecrawl scrape API](https://docs.firecrawl.dev/api-reference/endpoint/scrape)
- [Groq vision](https://console.groq.com/docs/vision)
- [Garmin FIT SDK](https://github.com/garmin/fit-java-sdk)
- [Polar AccessLink](https://www.polar.com/accesslink-api/)
- [Garmin OAuth2 PKCE](https://developerportal.garmin.com/sites/default/files/OAuth2PKCE_1.pdf)

## Automated AWS deployment

GitHub Actions runs unit tests, PostgreSQL integration tests, CDK assertions/synthesis, and a Docker build as a mandatory dependency of deployment. Successful runs on main deploy the application and infrastructure with CDK using AWS OIDC. See [CDK setup and deployment](infra/cdk/README.md) for the one-time AWS/GitHub configuration and migration notes.
