package com.gds.load;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.percent;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.randomSwitch;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Session;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

/**
 * A day at the GDS, compressed: travel agents shopping flights, hotels and cars, looking up their PNRs, and now
 * and then booking and ticketing. Every virtual user is one agent action, arriving at a fixed rate (an open
 * workload), so the backend sees the same request rate whether it keeps up or not.
 *
 * <p>Tuning, all optional, as {@code -Dname=value}:
 * <ul>
 *   <li>{@code baseUrl} backend address, default http://localhost:8080</li>
 *   <li>{@code rate} agent actions per second at full load, default 200</li>
 *   <li>{@code ramp} seconds to ramp up to that rate, default 30</li>
 *   <li>{@code duration} seconds to hold it, default 120</li>
 * </ul>
 * Total requests are roughly {@code rate x duration x 1.06} (a booking is four requests, not one).
 */
public class GdsSimulation extends Simulation {

    private static final String BASE_URL = System.getProperty("baseUrl", "http://localhost:8080");
    private static final double RATE = Double.parseDouble(System.getProperty("rate", "200"));
    private static final int RAMP = Integer.getInteger("ramp", 30);
    private static final int DURATION = Integer.getInteger("duration", 120);

    /** The 44 airports in the mock network. */
    private static final String[] AIRPORTS = (
            "JFK LAX ORD ATL DFW DEN SFO SEA MIA BOS YYZ YVR MEX CUN GDL MTY BOG LIM GRU EZE SCL LHR CDG FRA AMS MAD "
                    + "BCN FCO MUC ZRH IST DXB DOH JNB DEL BOM SIN HKG NRT ICN PEK BKK SYD MEL").split(" ");

    /** The seeded inventory starts today and covers 30 days; searches spread over the next three weeks. */
    private static final LocalDate TODAY = LocalDate.now();

    private static final List<String> TOKENS = login(20);

    // ---------------------------------------------------------------- feeders

    /** A random agent token, route and travel date for every virtual user. */
    private static final Iterator<Map<String, Object>> TRIPS = Stream.generate(() -> {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String origin = AIRPORTS[random.nextInt(AIRPORTS.length)];
        String destination;
        do {
            destination = AIRPORTS[random.nextInt(AIRPORTS.length)];
        } while (destination.equals(origin));
        LocalDate date = TODAY.plusDays(1 + random.nextInt(21));
        return Map.<String, Object>of(
                "token", TOKENS.get(random.nextInt(TOKENS.size())),
                "origin", origin,
                "destination", destination,
                "date", date.toString(),
                "checkOut", date.plusDays(1 + random.nextInt(4)).toString(),
                "days", 1 + random.nextInt(5),
                "lastName", surname(random));
    }).iterator();

    /** Passenger names are letters only, so a random six-letter surname. */
    private static String surname(ThreadLocalRandom random) {
        char[] name = new char[6];
        for (int i = 0; i < name.length; i++) {
            name[i] = (char) ('A' + random.nextInt(26));
        }
        return new String(name);
    }

    // ---------------------------------------------------------------- actions

    private static final String AUTH = "Bearer #{token}";

    private static final ChainBuilder SEARCH_AIR = exec(http("air search")
            .get("/api/shopping/air?origin=#{origin}&destination=#{destination}&date=#{date}&adults=1")
            .header("Authorization", AUTH)
            .check(status().is(200)));

    private static final ChainBuilder SEARCH_HOTELS = exec(http("hotel search")
            .get("/api/shopping/hotels?city=#{destination}&checkIn=#{date}&checkOut=#{checkOut}&rooms=1&guests=1")
            .header("Authorization", AUTH)
            .check(status().is(200)));

    private static final ChainBuilder SEARCH_CARS = exec(http("car search")
            .get("/api/shopping/cars?location=#{destination}&pickup=#{date}&days=#{days}")
            .header("Authorization", AUTH)
            .check(status().is(200)));

    private static final ChainBuilder RAW_INVENTORY = exec(http("raw flight inventory")
            .get("/api/inventory/flights?origin=#{origin}&destination=#{destination}&date=#{date}")
            .check(status().is(200)));

    private static final ChainBuilder LIST_PNRS = exec(http("list agency PNRs")
            .get("/api/pnrs?size=20")
            .header("Authorization", AUTH)
            .check(status().is(200)));

    /**
     * Shop, take the cheapest itinerary, create the PNR, retrieve it and ticket it. A sold-out class
     * (409) is a normal GDS answer under load, so it counts as a success.
     */
    private static final ChainBuilder BOOK_AND_TICKET = exec(http("book: air search")
            .get("/api/shopping/air?origin=#{origin}&destination=#{destination}&date=#{date}&adults=1")
            .header("Authorization", AUTH)
            .check(status().is(200))
            .check(jsonPath("$.outbound[0].segments[*].flightId").findAll().optional().saveAs("flightIds"))
            .check(jsonPath("$.outbound[0].segments[*].bookingClass").findAll().optional().saveAs("classes")))
            .doIf(session -> session.contains("flightIds")).then(
                    exec(http("book: create PNR")
                            .post("/api/pnrs")
                            .header("Authorization", AUTH)
                            .body(StringBody(GdsSimulation::pnrBody)).asJson()
                            .check(status().in(201, 409))
                            .check(jsonPath("$.recordLocator").optional().saveAs("locator")))
                    .doIf(session -> session.contains("locator")).then(
                            exec(http("book: retrieve PNR")
                                    .get("/api/pnrs/#{locator}")
                                    .header("Authorization", AUTH)
                                    .check(status().is(200)))
                            .exec(http("book: ticket")
                                    .post("/api/pnrs/#{locator}/ticket")
                                    .header("Authorization", AUTH)
                                    .body(StringBody("{\"formOfPayment\":\"CARD\",\"cardLast4\":\"4242\"}")).asJson()
                                    .check(status().in(200, 409)))));

    private static String pnrBody(Session session) {
        List<Object> flightIds = session.getList("flightIds");
        List<Object> classes = session.getList("classes");
        StringBuilder air = new StringBuilder();
        for (int i = 0; i < flightIds.size(); i++) {
            if (i > 0) {
                air.append(',');
            }
            air.append("{\"flightId\":").append(flightIds.get(i))
                    .append(",\"bookingClass\":\"").append(classes.get(i)).append("\"}");
        }
        return "{\"passengers\":[{\"type\":\"ADT\",\"firstName\":\"Agent\",\"lastName\":\"" + session.getString("lastName")
                + "\"}],\"segments\":{\"air\":[" + air + "]}}";
    }

    // ---------------------------------------------------------------- the mix

    private final ScenarioBuilder agents = scenario("Travel agents")
            .feed(TRIPS)
            .exec(randomSwitch().on(
                    percent(55.0).then(SEARCH_AIR),
                    percent(15.0).then(SEARCH_HOTELS),
                    percent(10.0).then(SEARCH_CARS),
                    percent(10.0).then(RAW_INVENTORY),
                    percent(8.0).then(LIST_PNRS),
                    percent(2.0).then(BOOK_AND_TICKET)));

    private final HttpProtocolBuilder protocol = http
            .baseUrl(BASE_URL)
            .acceptHeader("application/json")
            .userAgentHeader("gds-loadtest")
            // Agents share a connection pool, like a booking front end in front of a GDS would. Without this,
            // every virtual user opens its own connection and the client runs out of ports long before the server
            // runs out of capacity.
            .shareConnections();

    {
        setUp(agents.injectOpen(
                rampUsersPerSec(1).to(RATE).during(RAMP),
                constantUsersPerSec(RATE).during(DURATION)))
                .protocols(protocol)
                .assertions(global().failedRequests().percent().lt(1.0));
    }

    // ---------------------------------------------------------------- setup

    /** Logs in loadtest1..loadtestN once, up front. Password hashing is deliberately slow; searches shouldn't pay it. */
    private static List<String> login(int agents) {
        HttpClient client = HttpClient.newHttpClient();
        Pattern token = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");
        List<String> tokens = new ArrayList<>();
        for (int i = 1; i <= agents; i++) {
            String body = "{\"username\":\"loadtest" + i + "\",\"password\":\"loadtest\"}";
            try {
                HttpResponse<String> response = client.send(
                        HttpRequest.newBuilder(URI.create(BASE_URL + "/api/auth/login"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(body))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
                Matcher m = token.matcher(response.body());
                if (response.statusCode() != 200 || !m.find()) {
                    throw new IllegalStateException("Login failed for loadtest" + i + ": " + response.statusCode());
                }
                tokens.add(m.group(1));
            } catch (Exception e) {
                throw new IllegalStateException("Is the backend running at " + BASE_URL + "? " + e.getMessage(), e);
            }
        }
        return tokens;
    }
}
