# Deployment

The repository is a monorepo with a Spring Boot backend and a Next.js dashboard.

## Recommended hosting layout

- **Backend:** Railway (Java/Spring Boot; Railway's Railpack detects Java projects automatically)
- **Dashboard:** Vercel (first-class Next.js deployment)
- **MongoDB:** MongoDB Atlas
- **Redis:** Redis Cloud
- **Kafka:** Confluent Cloud

Railway can expose the Spring Boot HTTP service and the two custom WebSocket listeners through separate public domains targeting their internal ports. The application keeps its existing WebSocket implementation while making the bind host and ports configurable.

## Important: road-network data

The full processed Bengaluru road graph is intentionally not stored in the Git repository because it is large. A production deployment must mount the processed graph files before startup.

Recommended Railway setup:

1. Attach a persistent Railway Volume at `/app/data`.
2. Upload the processed `nodes.csv` and `edges.csv` under `/app/data/processed/bengaluru/` using the Railway CLI.
3. Set `FLEET_NODES_FILE=/app/data/processed/bengaluru/nodes.csv`.
4. Set `FLEET_EDGES_FILE=/app/data/processed/bengaluru/edges.csv`.
5. Set `FLEET_ALLOW_DEMO_GRAPH=false`.

This prevents a production instance from silently falling back to the tiny in-memory demo graph.

Example upload commands after the Railway volume exists:

```powershell
railway volume files upload <LOCAL_NODES_CSV> /data/processed/bengaluru/nodes.csv
railway volume files upload <LOCAL_EDGES_CSV> /data/processed/bengaluru/edges.csv
```

Use the exact local paths of the processed road-graph files on your machine. Railway volumes are mounted at runtime, so the files must be present before the application starts.

## Backend: Railway

Create a new Railway service from this GitHub repository.

Use the repository root (`/`) as the service root because `pom.xml` is at the repository root.

Recommended settings:

```text
Build command:
mvn -B -DskipTests package

Start command:
java -jar target/fleet-dispatch-engine-0.1.0-SNAPSHOT.jar

Healthcheck path:
/actuator/health/readiness
```

Required environment variables:

```text
MONGODB_URI=<MongoDB Atlas connection string>
REDIS_URL=<Redis Cloud connection string>
KAFKA_BOOTSTRAP_SERVERS=<Confluent bootstrap server>
KAFKA_API_KEY=<Confluent API key>
KAFKA_API_SECRET=<Confluent API secret>
KAFKA_SECURITY_PROTOCOL=SASL_SSL
KAFKA_SASL_MECHANISM=PLAIN
KAFKA_CONSUMER_GROUP=fleet-dispatch-engine
KAFKA_TOPIC=fleet.events
DRIVER_LOCATION_TTL=10m
FLEET_WS_BIND_HOST=0.0.0.0
FLEET_DRIVER_WS_PORT=8087
FLEET_DASHBOARD_WS_PORT=8088
FLEET_HEARTBEAT_TIMEOUT_MILLIS=10000
FLEET_NODES_FILE=/app/data/processed/bengaluru/nodes.csv
FLEET_EDGES_FILE=/app/data/processed/bengaluru/edges.csv
FLEET_ALLOW_DEMO_GRAPH=false
FLEET_API_ALLOWED_ORIGINS=<Vercel dashboard URL>
```

Railway injects `PORT`; Spring Boot binds to `${PORT:8080}` automatically. Do not hardcode the Railway-assigned HTTP port.

After the service is running, generate an HTTPS public domain for the Spring Boot HTTP port and configure two additional public domains targeting internal ports `8088` and `8087` for the dashboard and driver WebSocket listeners. Use the generated hostnames with `wss://` from the browser.

## Dashboard: Vercel

Import this repository into Vercel and set the **Root Directory** to:

```text
dashboard
```

The build command and output are the normal Next.js defaults.

Set these environment variables in Vercel:

```text
NEXT_PUBLIC_API_BASE_URL=https://<backend-public-domain>
NEXT_PUBLIC_DASHBOARD_WS_URL=wss://<dashboard-websocket-domain>/dashboard
NEXT_PUBLIC_DRIVER_WS_BASE_URL=wss://<driver-websocket-domain>/drivers
```

The values belong in Vercel's environment-variable UI, not in source control.

## Local development

Copy the root `.env.example` to `.env` and fill in your managed-service credentials. For the dashboard, copy `dashboard/.env.example` to the dashboard's local environment when needed.

Start the backend with:

```powershell
mvn spring-boot:run
```

Start the dashboard with:

```powershell
cd dashboard
npm install
npm run dev
```

## Deployment checks

Before calling the deployment stable, verify:

1. `GET /api/v1/health` returns `UP`.
2. `GET /actuator/health/readiness` returns HTTP 200.
3. `GET /api/v1/dashboard/summary` returns a valid summary.
4. The dashboard loads without localhost URLs in the browser console.
5. The dashboard WebSocket connects over `wss://`.
6. Driver telemetry WebSockets connect over `wss://`.
7. The application logs show the full Bengaluru road graph loaded from the mounted CSV files.
8. The live lifecycle scenario completes with a replacement driver and a completed order.
9. The GitHub Actions workflow passes both backend and dashboard jobs.

## Production notes

The application enables graceful Spring shutdown and uses the platform-provided `PORT`. Secrets remain runtime environment variables and are not stored in the repository.

This deployment target is intended as a stable public demonstration environment. It should not be described as a production fleet serving real customers without additional authentication/authorization, rate limiting, multi-instance state coordination, stronger observability, and a formal security/load review.
