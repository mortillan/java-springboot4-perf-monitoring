# Bottleneck Demo — Spring Boot 4 REST API

A tiny REST API built to **showcase two classic backend bottlenecks**, for the brownbag
session on *Spotting Software Engineering Bottlenecks Early*.

Each demo pairs a **slow** and a **fast** endpoint that return the **exact same JSON**,
so the only difference is database cost. You'll *measure* the difference (response
headers), *stress* it (k6 load test), and *see* it (distributed traces in Grafana Cloud).

**Demo 1 — N+1 problem (too many queries):**

| Endpoint | What it does | Cost (50 authors) |
| --- | --- | --- |
| `GET /api/slow/authors` | Loads authors, then lazily loads each author's books | **51** queries (1 + N) |
| `GET /api/fast/authors` | Loads authors and books in one `JOIN FETCH` | **1** query |

**Demo 2 — a single very slow query (one query, but expensive):**

| Endpoint | What it does | Cost (8,000 sales) |
| --- | --- | --- |
| `GET /api/slow-query/sales-ranking` | Correlated subquery — re-scans the whole table per row (O(N²)) | **1** query, ~**2,200 ms** |
| `GET /api/fast-query/sales-ranking` | `RANK() OVER (...)` window function (O(N log N)) | **1** query, ~**50 ms** |

**Lab Exercise — an upstream API bottleneck (for Q&A / hands-on):**

Unlike Demos 1 & 2, these two endpoints are **not** walked through below — they're left
as a **lab exercise**. The bottleneck here lives *outside* the database, in a slow
upstream HTTP dependency. Your job: use the traces to prove where the time goes.

| Endpoint | What it does |
| --- | --- |
| `GET /api/slow/upstream` | Calls a slow upstream API (`httpbin.org/delay/3`) |
| `GET /api/fast/upstream` | Calls a fast upstream API (`httpbin.org/get`) |

> See [Lab Exercise — find the upstream bottleneck](#lab-exercise--find-the-upstream-bottleneck)
> below for the questions to answer.

Every response includes headers that make the cost visible:

- `X-Sql-Statements` — how many SQL statements that request executed
- `X-Elapsed-Ms` — how long it took

> **Why two demos?** They look different to your tools. The N+1 shows up as a high
> **statement count** (`X-Sql-Statements: 51`). The slow query is just **one** statement
> (`X-Sql-Statements: 1`) that is internally huge — only `X-Elapsed-Ms` (and `EXPLAIN`)
> reveals it. Lesson: *measure the right thing.*

---

## Prerequisites

You need **Git** and a **JDK 17 or newer** (tested on 25). Everything else (the H2
database, Maven) is bundled or downloaded automatically.

| Tool | Required? | Why |
| --- | --- | --- |
| **Git** | ✅ | clone the repo |
| **JDK 17+** | ✅ | build & run the app |
| **k6** | optional | the load-testing part |
| **Grafana Cloud account** | optional (free) | the distributed-tracing part |

### Install Java (JDK 17+)

<details>
<summary><b>macOS (Homebrew)</b></summary>

```bash
brew install openjdk@25
```

Homebrew's `openjdk` is "keg-only", so add it to your PATH (then restart the terminal).
For **zsh** (`~/.zshrc`):

```bash
echo 'export PATH="/opt/homebrew/opt/openjdk@25/bin:$PATH"' >> ~/.zshrc
echo 'export JAVA_HOME="/opt/homebrew/opt/openjdk@25"' >> ~/.zshrc
source ~/.zshrc
```

> On Intel Macs the path is `/usr/local/opt/openjdk@25` instead of `/opt/homebrew/...`.
</details>

<details>
<summary><b>Windows</b></summary>

Easiest is winget (or download an installer from <https://adoptium.net>):

```powershell
winget install EclipseAdoptium.Temurin.21.JDK
```

The installer sets `JAVA_HOME` and PATH for you. Open a new terminal afterward.
On Windows, use `mvnw.cmd` instead of `./mvnw` in the commands below.
</details>

<details>
<summary><b>Linux (Debian/Ubuntu)</b></summary>

```bash
sudo apt-get update && sudo apt-get install -y openjdk-21-jdk
```

Or use SDKMAN (any distro): `curl -s "https://get.sdkman.io" | bash` then
`sdk install java 21-tem`.
</details>

Verify:

```bash
java -version    # should print 17 or higher
```

### Install k6 (only for the load-testing section)

```bash
brew install k6                       # macOS
winget install k6.k6                  # Windows
sudo apt-get install k6               # Linux (after adding the k6 apt repo)
```

See <https://grafana.com/docs/k6/latest/set-up/install-k6/> for other platforms.

---

## Clone and run

```bash
git clone https://github.com/<your-username>/bottleneck-springboot.git
cd bottleneck-springboot
```

Then run it with the bundled **Maven Wrapper** (`./mvnw` downloads the correct Maven
version automatically on first run — no Maven install needed):

```bash
./mvnw spring-boot:run            # macOS/Linux
mvnw.cmd spring-boot:run          # Windows
```

The app starts on **http://localhost:8080** and seeds an in-memory H2 database on
startup (no external DB to install). A quick smoke test:

```bash
curl -i http://localhost:8080/api/fast/authors
```

---

## The demo (do this live)

### Demo 1 — N+1 (too many queries)

```bash
# Slow: watch X-Sql-Statements: 51 and ~51 SELECTs scroll in the app console
curl -i http://localhost:8080/api/slow/authors

# Fast: same JSON body, but X-Sql-Statements: 1
curl -i http://localhost:8080/api/fast/authors

# Compare headers side by side
echo "SLOW:" && curl -s -D - -o /dev/null http://localhost:8080/api/slow/authors | grep -i "X-Sql-Statements\|X-Elapsed-Ms"
echo "FAST:" && curl -s -D - -o /dev/null http://localhost:8080/api/fast/authors | grep -i "X-Sql-Statements\|X-Elapsed-Ms"
```

### Demo 2 — a single very slow query

This one is sneaky: it is **one** SQL statement, so a "count the queries" check would
say it's fine. Only the timing gives it away.

```bash
echo "SLOW:" && curl -s -D - -o /dev/null http://localhost:8080/api/slow-query/sales-ranking | grep -i "X-Sql-Statements\|X-Elapsed-Ms"
echo "FAST:" && curl -s -D - -o /dev/null http://localhost:8080/api/fast-query/sales-ranking | grep -i "X-Sql-Statements\|X-Elapsed-Ms"
```

Expected (8,000 sales):

```
SLOW:  X-Sql-Statements: 1    X-Elapsed-Ms: ~2200
FAST:  X-Sql-Statements: 1    X-Elapsed-Ms: ~50
```

Both run **one** query and return the **same** ranking — but the slow one is ~40× slower.

**Crank up the data to make it more dramatic** (the slow query grows O(N²)):

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--demo.sales-count=15000
# or, if you built a jar:
java -jar target/bottleneck-springboot-0.0.1-SNAPSHOT.jar --demo.sales-count=15000
```

---

## Load testing under high load (k6)

`curl` shows the cost of **one** request. k6 simulates **many concurrent users**, which
is when bottlenecks really hurt — work piles up and latency explodes.

The script (`k6/compare.js`) ramps virtual users (VUs) up to a peak, holds, then ramps
down. Pick one endpoint per run with `TARGET`:

```bash
# Start the app first (any run option above), then in another terminal:
k6 run -e TARGET=slow-sales   k6/compare.js   # correlated subquery under load
k6 run -e TARGET=fast-sales   k6/compare.js   # window function under load
k6 run -e TARGET=slow-authors k6/compare.js   # N+1 under load
k6 run -e TARGET=fast-authors k6/compare.js   # JOIN FETCH under load
k6 run -e TARGET=slow-upstream k6/compare.js  # slow upstream API under load (lab exercise)
k6 run -e TARGET=fast-upstream k6/compare.js  # fast upstream API under load (lab exercise)

# Crank the load:
k6 run -e TARGET=slow-sales -e PEAK_VUS=50 k6/compare.js
```

Compare **`http_req_duration` p95/p99** between a slow run and its fast twin — the slow
endpoint's tail latency balloons, the fast one stays flat. (The `http_req_duration<500ms`
threshold goes **red** for the slow endpoints — that red is the lesson, not a bug.)

### Optional: stream k6 results to Grafana Cloud

k6 is a Grafana Labs product, so the same script works with Grafana Cloud:

```bash
k6 login cloud --token <YOUR_GRAFANA_CLOUD_K6_TOKEN>   # one-time
k6 run --out cloud -e TARGET=slow-sales k6/compare.js  # run locally, dashboards in cloud
```

Results appear under **Testing & synthetics → Performance** in Grafana Cloud.

---

## Distributed tracing in Grafana Cloud (find the slowest span)

To *see* which part of a request is slow — not just guess — run the app with an
**OpenTelemetry Java agent**. It auto-instruments incoming HTTP **and every JDBC query**,
so each request becomes a trace whose spans show exactly where time goes:

- **Slow query** → the sales-ranking endpoints run four named steps
  (`validate-request` → `load-ranking-slow` → `compute-summary` → `map-to-dto`). In the
  waterfall, `load-ranking-slow` (with its nested `SELECT sale` JDBC span) dwarfs the
  others — proof that the DB query, not parsing/serialization/business logic, is the
  bottleneck. The fast endpoint runs the same steps but `load-ranking-fast` is tiny too.
- **N+1** → one HTTP span with **~51 child `SELECT` spans** lined up underneath.

> The step spans come from `@WithSpan` annotations in `SalesRankingService`. They only
> appear when the agent is attached; without it they're a harmless no-op.

### Step 1 — Create a free Grafana Cloud account

1. Sign up at <https://grafana.com/auth/sign-up/create-user> (the free tier is plenty
   for this demo — includes traces, logs, and metrics).
2. In your stack, go to **Connections → Add new connection → OpenTelemetry (OTLP)**.
3. Choose the **Java** path. Grafana shows you two things:
   - a button to **download the Grafana OpenTelemetry Java agent** (a `.jar` file), and
   - the **environment variables** to set (your OTLP endpoint, protocol, and a base64
     `Authorization` header containing your instance ID + an API token it generates).

### Step 2 — Put the agent jar in the project

The agent jar is **not** included in this repo (it's git-ignored). Download it from
Step 1 and save it in the project root as:

```
bottleneck-springboot/grafana-opentelemetry-java.jar
```

> `scripts/run-with-tracing.sh` auto-detects this jar. (If you don't have one, the script
> falls back to downloading the upstream OpenTelemetry agent into `third_party/`.)

### Step 3 — Configure your credentials

Copy the template and paste in the values Grafana gave you:

```bash
cp .env.example .env
# then edit .env
```

`.env` (git-ignored — your token never gets committed):

```bash
OTEL_SERVICE_NAME=bottleneck-springboot
OTEL_EXPORTER_OTLP_ENDPOINT=https://otlp-gateway-prod-<your-zone>.grafana.net/otlp
OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
OTEL_RESOURCE_ATTRIBUTES=service.namespace=<you>,deployment.environment=local
OTEL_EXPORTER_OTLP_HEADERS="Authorization=Basic <base64 of INSTANCE_ID:API_TOKEN>"
```

> Your `<your-zone>` (e.g. `ap-southeast-1`, `us-east-0`, `eu-west-2`) and the base64
> header come straight from the Grafana OTLP page — just copy them.

### Step 4 — Run with tracing and view it

```bash
./mvnw -DskipTests package        # build the jar (first time / after code changes)
./scripts/run-with-tracing.sh     # attaches the agent, ships traces to Grafana Cloud
```

You should see `Using agent: grafana-opentelemetry-java.jar` and
`Tracing -> OTLP endpoint: ...`, with **no 401/403** lines.

Generate some traffic, then view the traces:

```bash
curl http://localhost:8080/api/slow-query/sales-ranking
curl http://localhost:8080/api/slow/authors
```

In Grafana Cloud: **Explore → Traces (Tempo)**. Filter by
`service.name = bottleneck-springboot`, **sort by newest**, open a slow trace, and read
the span waterfall — the longest bar is your optimization target.

> **No Grafana account yet?** You can still verify tracing locally. Run
> `./scripts/run-with-tracing.sh` **without** a `.env`, and spans print to the console
> (look for `LoggingSpanExporter` lines). Add `.env` later to ship them to the cloud.

> Sampling is set to **always_on** for the demo so every request is traced, and the span
> batch queue is enlarged (`OTEL_BSP_MAX_QUEUE_SIZE`) because the N+1 endpoint and startup
> seeding emit many JDBC spans. In production you'd sample a fraction.

### Step 5 — See the golden signals (p95, request rate, errors)

A single trace shows *where* time goes in **one** request, but it can't show aggregates
like **p95 latency**. Those come from **metrics** — and our setup already sends them: the
Grafana OpenTelemetry Java agent ships **traces + metrics + logs** (see
`scripts/run-with-tracing.sh`, which leaves the metrics exporter on in OTLP mode).

To turn those metrics into golden-signal dashboards, **enable Application Observability**
in Grafana Cloud (one-time):

1. In Grafana Cloud, open **Observability → Application Observability**.
2. If prompted, click **Enable** / **Get started** (it provisions the recording rules and
   dashboards that derive RED metrics from your OTLP data — free tier is fine).
3. Generate traffic (curl the endpoints, or run a k6 test), wait ~1–2 minutes for metrics
   to flow, then select the **`bottleneck-springboot`** service.

You'll get an auto-built **RED** view — **R**ate (req/s), **E**rrors (%), and **D**uration
with **p50 / p95 / p99** — broken down per route, so you can compare
`/api/slow-query/sales-ranking` against `/api/fast-query/sales-ranking` directly.

> Prefer a raw query? In **Explore → Metrics** you can compute p95 yourself:
> ```promql
> histogram_quantile(0.95, sum by (le, http_route) (rate(http_server_request_duration_seconds_bucket[5m])))
> ```

> Two complementary views for the brownbag: **Traces** answer *"where is the time inside a
> request?"*; **Application Observability** (and **k6**) answer *"how bad does p95 get in
> aggregate / under load?"*

---

## Lab Exercise — find the upstream bottleneck

Demos 1 & 2 are guided. This one is **yours to investigate** — it's the hands-on
Q&A portion of the session. Two endpoints call an external upstream API:

```bash
curl -i http://localhost:8080/api/slow/upstream   # calls httpbin.org/delay/3
curl -i http://localhost:8080/api/fast/upstream    # calls httpbin.org/get
```

The application's own code does almost nothing in both cases — yet one is dramatically
slower. Run them with tracing on (`./scripts/run-with-tracing.sh`), generate traffic,
open the traces in Grafana Cloud, and answer:

1. **Which span is the bottleneck?** Open a `/api/slow/upstream` trace and read the
   waterfall. Which span is the longest bar — is it your code, or something else?
2. **Is the database involved at all?** Compare this trace to a `/api/slow-query/sales-ranking`
   trace. What's different about where the time goes?
3. **What kind of span is the slow one?** What does its name / type tell you about the
   dependency causing the delay?
4. **Would `X-Elapsed-Ms` alone have told you the root cause?** Why is the *trace* (not
   just the timing header) the thing that points you at the upstream API?
5. **Under load (k6):** run `-e TARGET=slow-upstream` vs `-e TARGET=fast-upstream`. How
   does p95 differ? What does that say about depending on a slow third party?
6. **Bonus — how would you fix it?** If your own service is fast and the upstream is
   slow, where does the fix live? (Think: caching, timeouts, fallbacks, calling a
   faster/closer service — not optimizing your own code.)

<details>
<summary><b>Facilitator notes (the answer)</b></summary>

- The `call-upstream` span dominates, and nested under it is an **auto-instrumented HTTP
  CLIENT span** to `httpbin.org` — the OTel agent traces outbound calls just like JDBC.
  `validate-request` and `process-response` are slivers next to it.
- No JDBC spans appear at all — unlike Demos 1 & 2, the DB isn't in the picture. The time
  is spent *waiting on the network / upstream service*.
- The lesson: when **your** code is fast but the request is slow, the trace points
  outward — the bottleneck is a downstream dependency, and the fix lives there (or in how
  you call it: timeouts, retries with backoff, caching, circuit breakers, fallbacks),
  **not** in micro-optimizing your own code.
- Wiring: `UpstreamController` → `UpstreamApiService` (`@WithSpan` steps) →
  `RestClient` from `UpstreamClientConfig`. The slow path hits `/delay/{n}` (default 3s,
  override with `--demo.upstream.slow-delay-seconds=N`); the fast path hits `/get`. The
  upstream base URL is overridable with `--demo.upstream.base-url=...`.
- Heads-up: under sustained k6 load, `httpbin.org` may rate-limit. Lower the load with
  `-e PEAK_VUS=5`, or point `demo.upstream.base-url` at a local stub.

</details>

---

## How the bottlenecks are wired (for facilitators)

### Demo 1 — N+1

- `Author` has a **LAZY** `@OneToMany` to `Book` (`model/Author.java`). Lazy loading is
  what allows the extra-query-per-author behavior.
- `/api/slow/authors` calls `findAll()` then touches `author.getBooks()` for each author
  → Hibernate fires one extra `SELECT` per author (the "+N").
- `/api/fast/authors` calls a `@Query` with `left join fetch a.books`
  (`repository/AuthorRepository.java`) → one `SELECT` total.
- `open-in-view: false` (in `application.yml`) keeps the demo honest by forcing lazy
  loads to happen inside the `@Transactional` controller method.
- `SqlStatementCountFilter` reads Hibernate `Statistics` to report the per-request
  statement count via headers/logs.

### Demo 2 — slow single query

- `Sale` is a flat table seeded with `demo.sales-count` rows (default 8,000).
- `/api/slow-query/sales-ranking` runs a **correlated subquery**
  (`repository/SaleRepository.java`): for every row it re-runs
  `COUNT(*) ... WHERE s2.amount >= s.amount` against the whole table → O(N²).
- `/api/fast-query/sales-ranking` computes the identical ranking with a
  `RANK() OVER (ORDER BY amount DESC)` **window function** → one sort, O(N log N).
- Both are a single SQL statement, so `X-Sql-Statements` is `1` for each — the
  difference shows up only in `X-Elapsed-Ms`.
- `SalesRankingService` splits each request into `@WithSpan` steps so the trace clearly
  isolates the DB load from the cheap validate/summarize/map steps.

### Talking points

- **Measure, don't guess:** the header turns "feels slow" into a number.
- **The fix isn't more hardware:** 51 → 1 query, or O(N²) → O(N log N), is a code change.
- **It hides in small data:** with 50 rows both demos look instant; at scale they fall over.
- **Count vs cost are different signals:** N+1 is caught by *query count*; a slow query is
  caught only by *timing/`EXPLAIN`*. A good engineer checks both.
- **Same lesson, any stack:** N+1 → NestJS/TypeORM `relations` / `QueryBuilder` joins, or
  GraphQL DataLoader batching. Slow query → avoid correlated subqueries/non-sargable
  predicates; use window functions, proper indexes, and read `EXPLAIN` output.

---

## Tech

- Spring Boot **4.1.0** (Spring Framework 7), Java 17+
- Spring Web + Spring Data JPA (Hibernate)
- H2 in-memory database (seeded on startup — no external DB needed)
- **k6** for load testing (optionally streams to Grafana Cloud k6)
- **Grafana OpenTelemetry Java agent** for distributed tracing (ships to Grafana Cloud / Tempo)

## Reset the data

The H2 database is in-memory and re-seeded on every startup — just restart the app.
