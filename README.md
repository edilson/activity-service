# Cycling activity service

Java 21 / Spring Boot service for GPX, FIT, PNG/JPEG screenshots, and public Strava activity links. PostgreSQL stores normalized activities, original source files, extraction responses, route coordinates, encrypted provider connections, and the transactional SQS outbox.

## Run locally with Docker Compose

1. Copy `.env.example` to `.env` and set `DATABASE_PASSWORD`.
2. For Cognito, set `COGNITO_ISSUER`, `COGNITO_CLIENT_ID`, `COGNITO_CLIENT_SECRET`, and `COGNITO_DOMAIN` as described below. Keep `AUTH_MODE=cognito`.
3. Run:

```sh
docker compose up --build -d
docker compose logs -f app
```

Compose builds the non-root application container, starts PostgreSQL 17 with a persistent volume, starts LocalStack, creates both FIFO queues, waits for dependencies, and exposes the application at http://localhost:8080. Ports are bound to localhost. The app connects to `postgres:5432` and `localstack:4566` inside the Docker network. `SQS_ENABLED=true` enables local publishing; otherwise events remain pending in PostgreSQL.

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
- `activity_sources`: exact original GPX/FIT/screenshot bytes (`bytea`), filename, detected/assigned content type, original submitted URL, full provider response (`jsonb`), and extracted route coordinates (`jsonb`).
- GPX and FIT originals retain all source fields, including extension/developer fields and samples that are not mapped into normalized columns. Download the original for complete reprocessing.
- Groq responses retain all returned fields and the extracted JSON text. The extraction prompt requests additional visible activity fields (heart rate, cadence, speed, power, device, date, splits, etc.). The original screenshot is also retained because OCR cannot guarantee that every visible value is extracted accurately.
- Firecrawl requests structured JSON, Markdown, and raw HTML; the entire response is retained, including additional fields and metadata. Only content Firecrawl can retrieve is available; private or inaccessible Strava data cannot be recovered.

Source data, normalized activity, and the outbox event commit atomically. Listing activities does not return source blobs. Owned source metadata/JSON is available through `/api/activities/{id}/source`; the original binary is available as an attachment through `/api/activities/{id}/file`. Neither endpoint permits access to another user's activity. Raw HTML is returned as JSON data, never rendered as an HTML page.

Flyway migration V2 adds source storage without rewriting V1 or removing existing activities. Old imports cannot have their discarded source files reconstructed; their source endpoint returns 404 until reimported. The previous H2 runtime database is not automatically migrated into PostgreSQL. Existing local-account activities also require an explicit owner mapping when moving to Cognito subjects; the service does not silently transfer ownership.

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

Uploads are limited to 20 MiB, screenshots to 4 MiB with PNG/JPEG signature checks, and extracted routes to 100000 points. Original binary content is retained in PostgreSQL rather than discarded or written to container storage.

## Firecrawl, Groq, and provider connections

Set `FIRECRAWL_API_KEY` and `GROQ_API_KEY`. Firecrawl uses `/v2/scrape`. Submit full HTTPS `strava.com/activities/{id}` URLs; expand short/share links first. URL query parameters are removed from the scraping request, while the original submitted URL is retained in owned source storage. Upload GPX/FIT for private or login-only rides.

Groq uses base64 image input and JSON mode at `/openai/v1/chat/completions`. `GROQ_VISION_MODEL` defaults to `qwen/qwen3.6-27b`. Outbound connect/read deadlines are 10/90 seconds; failed paid extractions are not retried automatically.

For Polar/Garmin, set their client IDs/secrets, `PUBLIC_BASE_URL`, and a base64-encoded random 32-byte `OAUTH_ENCRYPTION_KEY`. Register `/oauth/polar/callback` and `/oauth/garmin/callback` at your public origin. Keep the encryption key across restarts; stored tokens use AES-256-GCM, with owner/provider authenticated context. Changing the key requires migrating ciphertext.

Provider state is bound to the browser session, expires in ten minutes, and is consumed once. Garmin uses PKCE, user-ID lookup, and an explicit refresh endpoint with row locking. Polar uses Basic client authentication and registers the user with AccessLink. These connections do not automatically import provider history or subscribe to webhooks. Garmin requires developer-program approval and Polar requires an AccessLink client and user consents. Pending sessions are in memory; multiple instances need sticky sessions or shared session storage.

## SQS FIFO

The local stack initializes `activities-short.fifo` and `activities-long.fifo`. Enable publishing with `SQS_ENABLED=true`. For AWS, `infra/sqs.yml` provisions two encrypted FIFO queues with FIFO dead-letter queues and redrive after five failed receives. Supply the resulting URLs, region, and workload credentials; grant `sqs:SendMessage` on both ARNs.

- Short queue: distance **< 100000 meters**.
- Long queue: distance **>= 100000 meters**, including exactly 100 km.
- Message group: stable owner hash; deduplication ID: persistent event UUID.

The dispatcher locks up to 20 due outbox rows every five seconds. Failed sends back off up to one hour. Disabled SQS leaves events pending. Messages contain an activity reference and distance, avoiding large route payloads. Downstream consumers must deduplicate persistent event IDs because a crash after SQS acceptance can cause later redelivery beyond the five-minute SQS deduplication window. FIFO preserves SQS acceptance order within a group, not original import order across retries or separate queues. Published outbox rows are retained; configure retention and pending-age monitoring for your deployment.

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
