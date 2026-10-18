# GDS: a Global Distribution System from scratch

A portfolio project that rebuilds the core of a travel GDS (in the spirit of Amadeus, Sabre or Travelport):
mocked airline, hotel and car-rental suppliers, shopping, booking (PNRs), ticketing, and an agent console,
built to take millions of simulated requests.

- **Backend:** Java 21, Spring Boot 3.5, virtual threads, H2, Redis
- **Frontend:** React 19, TypeScript, Vite
- **Load testing:** Gatling, Prometheus, Grafana

## Roadmap

| Step | Scope | Status |
|------|-------|--------|
| 1. Foundation and inventory | Project setup, virtual threads, H2, Redis, Docker Compose, mock supplier inventory for flights, hotels and cars | Done |
| 2. Core GDS flows | Availability and shopping, PNR create/retrieve/modify, holds with expiry, pricing and fare rules, ticketing, cancellation and refunds, queues, agency and agent auth | Done |
| 3. React agent console | Search, book, PNR view, ticketing, queues, admin, live throughput dashboard | Done |
| 4. Load simulation | Gatling scenarios at millions of requests, virtual vs platform threads, Prometheus metrics, results write-up | Done |

## Run it

You need **JDK 21** and **Node 20+**. Docker is optional: it's the easiest way to get Redis.

**One-time setup for the Maven wrapper** (so you don't need Maven installed). The wrapper's config lives in
`backend/.mvn/wrapper/maven-wrapper.properties`. If that file is missing, create it from `backend` in PowerShell:

```powershell
mkdir .mvn\wrapper -Force
Set-Content .mvn\wrapper\maven-wrapper.properties "distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip"
```

If you already have Maven, or you run the app from IntelliJ, you can skip this and use `mvn` in place of `./mvnw`.

```bash
# 1. Redis (optional: without it the app still works and reads straight from H2)
docker compose up -d redis

# 2. Backend on http://localhost:8080
cd backend
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run

# 3. Frontend on http://localhost:5173
cd frontend
npm install
npm run dev
```

Open http://localhost:5173 and sign in with one of the demo logins (the login screen has buttons that fill them in).
The live dashboard at http://localhost:5173/#/dashboard also works without signing in.

The first start generates the inventory into `backend/data/gds.mv.db` (about 4 seconds, about 45 MB).
Later starts reuse it. Delete the `backend/data` folder to regenerate.

Run the backend tests with `./mvnw test`. They use an in-memory H2 database and a local cache, so they don't need Redis.

> Upgrading from step 1? Delete `backend/data` once so the new tables and demo logins are created cleanly.

## What step 1 gives you

### Mock supplier inventory

Generated on first start with a fixed random seed, so every machine gets the same data. The default is 30 days of inventory:

| Product | What's generated | Rows (30 days) |
|---------|------------------|----------------|
| Air | 44 real airports, 15 fictional airlines in hub-and-spoke networks, dated departures with timezone-correct arrival times, aircraft sized by distance | 20,880 flights |
| Air fares | Booking classes per flight (F, J/C, W, Y/B/M/H/K/Q) with distance-based fares and nested availability, so cheap classes close first as a flight fills | 172,020 classes |
| Hotels | 6 properties per city across 6 fictional chains, 4 room types, nightly availability with weekend peaks | 31,680 room-nights |
| Cars | 5 fictional vendors × 9 ACRISS categories at every airport | 59,400 rows |

Volume is set in `application.yml` under `gds.seed` (`days`, `hotels-per-city`).

### Virtual threads

`spring.threads.virtual.enabled=true` puts every Tomcat request on a Java 21 virtual thread. You can check it at
`GET /api/system/stats`, which reports the thread that served the request
(for example `VirtualThread[#51,tomcat-handler-3]`).

### Redis caching

There are two cache levels. Each instance keeps a small in-memory cache (Caffeine) in front of Redis, which is shared
by every instance. Reference data is cached for 6 hours. Inventory snapshots stay 5 minutes in Redis and 10 seconds
in memory, and every booking, cancellation and expired hold evicts the entries it changed from both levels, so a
search sees new seat counts right away. Redis stores plain, typed JSON
(`redis-cli get "gds:flight-inventory::JFK:LHR:2026-11-02"`). If Redis is unreachable, the errors are logged and
requests fall through to the in-memory level and H2, so the app never fails because the cache is down.

## Step 2: booking flows

### Logins

Agents log in with a username and password and get a JWT, valid for 8 hours. Send it as `Authorization: Bearer <token>`.
These demo logins are created on first start:

| Username | Password | Role | Agency (PCC) |
|----------|----------|------|--------------|
| `admin` | `admin123` | ADMIN, sees every agency and manages agencies and agents | GDS1HQ |
| `supervisor` | `super123` | SUPERVISOR | NYC1A2 Hudson Travel |
| `agent1`, `agent2` | `agent123` | AGENT | NYC1A2 Hudson Travel |
| `mexagent` | `agent123` | AGENT | MEX1B3 Viajes Pirámide |
| `loadtest1` … `loadtest20` | `loadtest` | AGENT, for step 4 | LOAD01 |

Agents only see their own agency's PNRs and queues. Another agency's PNR answers 404, as if it didn't exist.

### The PNR lifecycle

```
shop ──► create PNR (HELD, inventory held, on TICKETING queue)
              │  add or cancel segments, update contact while held
              ├──► ticket before the deadline ──► TICKETED ──► cancel ──► CANCELLED, refund, on REFUNDS queue
              ├──► cancel ──► CANCELLED, inventory released
              └──► deadline passes ──► EXPIRED, inventory released, on EXPIRED queue
```

- **Shopping** returns nonstops and one-stop connections over the airline hubs that serve both ends of the trip, with a 60 minute to 10 hour connection
  window (an overnight connection is allowed). It picks the cheapest booking class in the requested cabin with enough seats
  for the whole party, prices it per passenger type and sorts by total price.
- **Pricing:** children pay 75% and infants on a lap pay 10% and don't take a seat. Taxes are mocked: a per-segment
  airport charge plus 7.5%, 14% hotel tax and 16% car tax.
- **Fare rules** by booking class: FLEX (F, J, Y) is fully refundable, STANDARD (C, W, B) refunds less 100,
  SAVER (M, H) refunds less 200, and BASIC (K, Q) refunds taxes only. Cancelling within 24 hours of ticketing
  voids the tickets for a full refund.
- **Holds:** a new PNR holds its seats, rooms and cars for 30 minutes (`gds.booking.hold-time`). A background job
  releases holds that weren't ticketed in time.
- **Tickets:** one 13-digit e-ticket per passenger, made of the validating airline's 3-digit code plus a 10-digit serial.

### How it stays correct under load

- **No overselling, no lock retries.** Every hold is a single conditional update, `seats_available = seats_available - n
  where seats_available >= n`, so the database checks and writes in one step. A test fires 40 concurrent bookings at a
  class with 5 seats left: exactly 5 succeed and 35 get `409 Conflict`.
- **All or nothing.** A PNR with air, hotel and car segments is one transaction. If the hotel has no rooms, the seats
  already taken are rolled back too.
- **Fresh availability.** After a booking commits, the cached availability for the routes, nights and days it touched
  is evicted, so the next search sees the new seat counts.
- **Parallel fan-out on virtual threads.** One air search reads several route and day combinations (the nonstop plus
  both legs over each hub that serves the trip, on the same and the next day). They run at once on virtual threads,
  so a search costs about one cache round trip instead of one per route.
- **Concurrent edits.** PNRs carry a version number. If two agents change the same PNR at once, the second gets
  `409` and retrieves it again.

## Step 3: the agent console

The React app in `frontend/` is what a travel agent would work in all day. Every screen talks to the API from step 2.

| Screen | What it does |
|--------|--------------|
| Login | Signs in with an agent ID and stores the token. Demo login buttons fill in the seeded accounts. |
| Search | Flights (one way or round trip, adults, children, infants, cabin), hotels and cars. Results show nonstop and one-stop connections, fare family, seats left and the search time in milliseconds. Anything picked goes into the booking basket. |
| Booking | Reviews the basket, collects one name per traveller in the party plus contact details, and creates the PNR. Seats, rooms and cars are held for 30 minutes. |
| PNRs | Retrieve by record locator, or filter your agency's PNRs by last name and status. |
| PNR view | Passengers and contact (editable), flight, hotel and car segments with per-segment cancel, a live countdown to the ticketing deadline, price breakdown, ticketing with card, cash or invoice, cancel with void or refund, placing on a queue, and the full history. |
| Queues | Ticketing, expired, refunds and general queues with counts. Open the PNR or mark an item worked. |
| Dashboard | Polls `/api/system/metrics` every 2 seconds and shows requests per second, average latency, cache hit rate, DB connections in use, heap, platform threads and PNRs by status, each with a 3-minute sparkline. Useful to watch while the step 4 load test runs. |
| Inventory | The raw seat, room and car counts behind shopping, without pricing. |
| Admin | Admins only. Create and deactivate agencies and agents. |

The console follows the system light or dark theme and works on a phone-sized screen.

Under the hood: React Router with hash URLs (so a static build works anywhere), a small typed API client in
`src/api.ts`, and React context for the session, airports and basket in `src/state.tsx`. The Vite dev server proxies
`/api` and `/actuator` to the backend on port 8080, so there is no CORS setup in development.

`npm run build` writes a static site to `frontend/dist`.

## Step 4: load simulation

`loadtest/` is a Gatling project that plays a crowd of travel agents against the backend. Each virtual user is one
agent action, arriving at a fixed rate whether or not the server keeps up (an open workload, which is how real
traffic behaves):

| Share | Action |
|------:|--------|
| 55% | Air search on a random pair of the 44 airports, 1 to 21 days out |
| 15% | Hotel search, 1 to 4 nights |
| 10% | Car search, 1 to 5 days |
| 10% | Raw flight inventory read |
| 8% | List the agency's PNRs |
| 2% | Book: search, create a PNR with the cheapest itinerary, retrieve it, ticket it |

The 20 `loadtest` agents log in once before the run, so password hashing doesn't skew the numbers. A sold-out class
(`409`) counts as a normal answer, not a failure.

### Run it

Start Redis and the backend as usual, then from `loadtest`:

```bash
../backend/mvnw gatling:test                                      # Windows: ..\backend\mvnw.cmd gatling:test
../backend/mvnw gatling:test -Drate=2000 -Dramp=60 -Dduration=300 # 2,000 actions per second for 5 minutes
```

| Setting | Default | Meaning |
|---------|---------|---------|
| `-Drate` | 200 | Agent actions per second at full load |
| `-Dramp` | 30 | Seconds to ramp up to that rate |
| `-Dduration` | 120 | Seconds to hold it |
| `-DbaseUrl` | http://localhost:8080 | Backend address |

Gatling prints a summary and writes an HTML report to `loadtest/target/gatling/`. Watch the run live on the console
dashboard at http://localhost:5173/#/dashboard, or in Grafana:

```bash
docker compose --profile monitoring up -d   # Prometheus on :9090, Grafana on :3000, dashboard "GDS backend"
```

Two backend settings exist for these experiments:

| Setting | Default | What it does |
|---------|---------|--------------|
| `GDS_SUPPLIER_LATENCY` (`gds.supplier.latency`) | `0ms` | Every air, hotel and car search waits this long, standing in for the round trip to the airline's, hotel's or car vendor's own system. Real GDS traffic is mostly this kind of waiting. |
| `--spring.threads.virtual.enabled=false` | `true` | Serve requests on Tomcat's 200 platform threads instead of virtual threads |

For example, the platform-thread runs below were started with
`java -jar target/gds-backend-0.1.0-SNAPSHOT.jar --spring.threads.virtual.enabled=false --gds.supplier.latency=200ms`.

### Results

Measured on a 4-vCPU, 16 GB Linux container with Java 21, a 2 GB heap, H2 and Redis on the same machine, **and Gatling
on the same 4 CPUs**. A dedicated load generator would leave more CPU for the backend. Every run started from a fresh
database and an empty cache.

**Virtual threads compared with platform threads**, with 200 ms of supplier latency on every search (60-second ramp,
then 2 minutes at full rate):

| Offered load | Threads | Requests | Failed | Throughput | p50 | p95 | p99 |
|-------------:|---------|---------:|-------:|-----------:|----:|----:|----:|
| 1,000/s | virtual | 155,064 | 0 | 1,035/s | 201 ms | 204 ms | 224 ms |
| 1,000/s | platform | 154,956 | 0 | 1,035/s | 201 ms | 204 ms | 221 ms |
| 2,000/s | virtual | 309,999 | 0 | 2,070/s | 201 ms | 213 ms | 248 ms |
| 2,000/s | platform | 306,649 | **98,926 (32%)** | 1,220/s | 6.7 s | 10 s | 10 s |

(Offered load counts agent actions; a booking is four requests, so the server sees about 3.5% more. Throughput is
the server's rate at full load; p50 to p99 are over every request in the run. The failed platform
requests mostly couldn't connect within Gatling's 10-second connect timeout.)

At 1,000 requests a second the two are identical: about 160 requests are waiting on a supplier at any moment, and
200 platform threads are enough. At 2,000 a second about 330 requests are waiting at once. With platform threads, 200 of
them hold a thread each, Tomcat tops out at 200 threads ÷ 0.16 s ≈ 1,220 requests a second, and everything else
queues until it times out. The live dashboard itself stopped updating for three minutes because its own requests were
stuck in that queue. With virtual threads, a request waiting on a supplier parks without holding an OS thread: the JVM
ran the same load with about 30 platform threads, and latency stayed at the supplier's 200 ms plus a few
milliseconds.

**A million requests**: virtual threads, 200 ms supplier latency, ramp to 2,500 actions a second, hold for
6 minutes 40 seconds:

| Requests | Failed | Throughput at full load | p50 | p95 | p99 | Max | PNRs ticketed |
|---------:|-------:|------------------------:|----:|----:|----:|----:|--------------:|
| 1,110,463 | 0 | about 2,600/s | 202 ms | 239 ms | 292 ms | 1.2 s | 11,811 |

98.3% of the 4.1 million inventory lookups were answered from cache (4,041,313 hits, 70,841 misses).

**Ceiling without supplier latency:** ramping to 3,000 actions a second and holding for a minute (278,817 requests) gave no errors,
p95 43 ms and p99 131 ms. Past that, the 4 CPUs are the limit.

### What the load test found and fixed

The first runs failed well below these numbers. Each fix below is in the code:

1. **Redis JSON decoding was the bottleneck.** An air search read up to 46 route-days, each a Redis round trip plus JSON
   decoding, and the backend ran out of CPU near 500 requests a second. Fix: an in-memory cache level in front of Redis
   (`config/TwoLevelCache.java`) that returns already-decoded objects.
2. **Searches asked for routes that don't exist.** Every search looked up both legs over all 15 hubs, though a given
   airport is served by one or two of them. Fix: a route index (`flight/RouteNetwork.java`) loaded once from the
   schedule, so a search only reads legs that are flown.
3. **A cold cache caused a stampede.** At startup, hundreds of concurrent searches missed the cache for the same keys
   and all queried H2 at once. Fix: `@Cacheable(sync = true)` with a single load per key. The waiting uses a
   `CompletableFuture`, not `synchronized`, because on Java 21 a virtual thread blocked inside `synchronized` pins its
   carrier thread.
4. **Cache hits borrowed database connections.** `@Transactional` sat outside `@Cacheable`, so even a cache hit took one
   of the 50 pooled connections. Fix: `@EnableCaching(order = HIGHEST_PRECEDENCE)` so the cache answers first.
5. **Virtual threads have no built-in limit.** Overloaded, the server accepted every request, filled the 2 GB heap
   with half-finished work and died with `OutOfMemoryError`. Platform threads have an accidental limit (200 threads);
   virtual threads need a deliberate one. Fix: `config/ConcurrencyLimitFilter.java` allows 1,000 API requests in
   progress (`gds.http.max-concurrent-requests`). Past that, a request waits up to a second for a slot, then gets a quick
   `503` with `Retry-After`. The server stays up and keeps serving at full speed.

### Metrics

The backend publishes Micrometer metrics at `/actuator/prometheus`. Beyond the standard JVM, Tomcat, HikariCP and
HTTP metrics (with latency histograms for p95 and p99), it adds:

| Metric | Meaning |
|--------|---------|
| `cache_gets_total{cache, result, level}` | Cache lookups answered by the in-memory level, by Redis, or missed |
| `gds_supplier_calls_seconds` | Simulated supplier round trips, by type (air, hotel, car) |
| `gds_http_in_flight` | API requests in progress |
| `gds_http_rejected_total` | Requests turned away at the concurrency limit |

The Grafana dashboard in `monitoring/grafana/dashboards/gds-overview.json` charts throughput, p95 and p99 latency,
errors and rejections, requests in progress, cache hits by level, supplier latency, database connections, threads and
heap.

## API

Public, no login needed:

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/auth/login` | `{"username","password"}` returns a JWT |
| GET | `/api/system/stats` | Row counts, inventory window, runtime and thread info, Redis status |
| GET | `/actuator/prometheus` | Prometheus metrics |
| GET | `/api/system/metrics` | Live counters for the dashboard: requests, latency, cache hits and misses, DB pool, heap, threads, PNRs by status |
| GET | `/api/reference/airports`, `/api/reference/airlines` | Reference data (cached) |
| GET | `/api/inventory/flights?origin=JFK&destination=LHR&date=…` | Raw seats and fares per booking class |
| GET | `/api/inventory/hotels?city=CDG&date=…`, `/api/inventory/cars?location=MEX&date=…` | Raw room and car inventory |
| GET | `/actuator/prometheus` | Metrics |
| GET | `/h2-console` | H2 console (JDBC URL `jdbc:h2:file:./data/gds`, user `sa`, no password) |

Agent login required:

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/auth/me` | Who the token belongs to |
| GET | `/api/shopping/air?origin=MEX&destination=LHR&date=…&returnDate=…&adults=2&children=0&infants=0&cabin=ECONOMY` | Priced offers, outbound and inbound |
| GET | `/api/shopping/hotels?city=CDG&checkIn=…&checkOut=…&rooms=1&guests=2` | Hotels with rooms free on every night |
| GET | `/api/shopping/cars?location=MEX&pickup=…&days=3` | Cars free on every day |
| POST | `/api/pnrs` | Create a PNR (example below) |
| GET | `/api/pnrs?status=HELD&lastName=SMITH&page=0&size=20` | Search the agency's PNRs |
| GET | `/api/pnrs/{locator}` | Retrieve a PNR with prices, tickets and history |
| POST | `/api/pnrs/{locator}/segments` | Add air, hotel or car segments (same shape as `segments` below) |
| DELETE | `/api/pnrs/{locator}/segments/{AIR\|HOTEL\|CAR}/{id}` | Cancel one segment and release its inventory |
| PATCH | `/api/pnrs/{locator}/contact` | Update email and phone |
| POST | `/api/pnrs/{locator}/ticket` | `{"formOfPayment":"CARD","cardLast4":"4242"}` (or `CASH`, `INVOICE`) |
| POST | `/api/pnrs/{locator}/cancel` | Cancel, release inventory, void or refund tickets |
| GET | `/api/queues` | Item count per queue |
| GET | `/api/queues/{TICKETING\|EXPIRED\|REFUNDS\|GENERAL}` | Items on a queue |
| POST | `/api/queues/{queue}` | `{"recordLocator","remark"}` places a PNR on a queue |
| DELETE | `/api/queues/items/{id}` | Mark a queue item as worked |
| GET, POST, PATCH | `/api/admin/agencies`, `/api/admin/agents` | ADMIN only: list, create, deactivate |

Create a PNR with `flightId` and `bookingClass` taken from a shopping offer, and `roomTypeId` from hotel shopping:

```json
{
  "passengers": [
    { "type": "ADT", "firstName": "Grace", "lastName": "Hopper" },
    { "type": "INF", "firstName": "Ann", "lastName": "Hopper", "dateOfBirth": "2025-08-01" }
  ],
  "contactEmail": "grace@example.com",
  "segments": {
    "air":    [ { "flightId": 1234, "bookingClass": "M" } ],
    "hotels": [ { "roomTypeId": 56, "checkIn": "2026-11-02", "checkOut": "2026-11-05", "rooms": 1 } ],
    "cars":   [ { "vendor": "AX", "location": "LHR", "sipp": "CCAR", "pickupDate": "2026-11-02", "days": 3 } ]
  }
}
```

Errors use the standard problem format, for example
`409 {"title":"Conflict","detail":"Class Q on PC104 2026-10-18 is sold out for 2 seat(s)"}`.

## Project layout

```
backend/
  src/main/java/com/gds/
    config/      two-level cache, concurrency limit, CORS and typed configuration
    security/    JWT login and the current agent
    agency/      agencies, agents, admin endpoints
    reference/   airports and airlines
    flight/      flights and booking-class inventory
    hotel/       hotels, room types, nightly room inventory
    car/         car rental inventory
    seed/        deterministic mock-data generator
    shopping/    air (with connections), hotel and car shopping
    pricing/     passenger-type pricing, taxes, fare rules
    supplier/    simulated latency of airline, hotel and car systems
    booking/     PNRs, inventory holds, ticketing, refunds, hold expiry
    queue/       agency work queues
    system/      inventory, stats and live metrics endpoints
frontend/
  src/
    api.ts       typed API client
    state.tsx    session, airports and booking basket
    components/  shared UI pieces
    pages/       one file per screen
loadtest/
  src/test/java/com/gds/load/GdsSimulation.java   Gatling scenario
monitoring/      Prometheus config and the Grafana dashboard
docker-compose.yml
```
