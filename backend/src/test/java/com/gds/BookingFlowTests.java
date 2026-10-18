package com.gds;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.fasterxml.jackson.databind.JsonNode;
import com.gds.booking.HoldExpiryJob;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BookingFlowTests {

    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    HoldExpiryJob expiryJob;

    String agent;

    @BeforeEach
    void login() {
        agent = token("agent1", "agent123");
    }

    // ---------------------------------------------------------------- auth

    @Test
    void bookingApiNeedsALogin() {
        assertThat(http.getForEntity("/api/pnrs", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ResponseEntity<String> wrong = http.postForEntity("/api/auth/login",
                Map.of("username", "agent1", "password", "nope"), String.class);
        assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void onlyAdminsManageAgencies() {
        assertThat(call(HttpMethod.GET, "/api/admin/agencies", agent, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<JsonNode> asAdmin = call(HttpMethod.GET, "/api/admin/agencies", token("admin", "admin123"), null);
        assertThat(asAdmin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(asAdmin.getBody().size()).isEqualTo(4);
    }

    // ---------------------------------------------------------------- shopping

    @Test
    void airShoppingFindsNonstopsAndConnections() {
        JsonNode result = ok(call(HttpMethod.GET,
                "/api/shopping/air?origin=MEX&destination=LHR&date=" + TOMORROW + "&returnDate=" + TOMORROW.plusDays(1),
                agent, null));

        JsonNode outbound = result.get("outbound");
        assertThat(outbound.size()).isPositive();
        // nobody flies MEX-LHR nonstop in the mock network, so every option connects (e.g. over JFK or MAD)
        outbound.forEach(offer -> {
            assertThat(offer.get("stops").asInt()).isEqualTo(1);
            JsonNode legs = offer.get("segments");
            assertThat(legs.get(0).get("destination").asText()).isEqualTo(legs.get(1).get("origin").asText());
        });
        assertThat(result.get("inbound").size()).isPositive();
        // offers come back cheapest first
        assertThat(outbound.get(0).at("/price/grandTotal").decimalValue())
                .isLessThanOrEqualTo(outbound.get(outbound.size() - 1).at("/price/grandTotal").decimalValue());
    }

    @Test
    void hotelAndCarShoppingPriceTheWholeStay() {
        JsonNode hotels = ok(call(HttpMethod.GET, "/api/shopping/hotels?city=CDG&checkIn=" + TOMORROW + "&checkOut="
                + TOMORROW.plusDays(2) + "&rooms=1&guests=2", agent, null));
        JsonNode room = hotels.get(0).get("rooms").get(0);
        assertThat(room.at("/price/nights").asInt()).isEqualTo(2);
        assertThat(room.at("/price/total").decimalValue()).isGreaterThan(room.at("/price/base").decimalValue());

        JsonNode cars = ok(call(HttpMethod.GET, "/api/shopping/cars?location=MEX&pickup=" + TOMORROW + "&days=2", agent, null));
        assertThat(cars.get(0).at("/price/nights").asInt()).isEqualTo(2);
    }

    // ---------------------------------------------------------------- PNR lifecycle

    @Test
    void bookTicketAndVoidOnTheSameDay() {
        JsonNode offer = firstOffer("JFK", "LHR");
        JsonNode segment = offer.get("segments").get(0);
        int seatsBefore = seatsLeft(segment);
        JsonNode hotel = ok(call(HttpMethod.GET, "/api/shopping/hotels?city=LHR&checkIn=" + TOMORROW + "&checkOut="
                + TOMORROW.plusDays(2), agent, null)).get(0).get("rooms").get(0);
        JsonNode car = ok(call(HttpMethod.GET, "/api/shopping/cars?location=LHR&pickup=" + TOMORROW + "&days=2", agent, null)).get(0);

        var created = call(HttpMethod.POST, "/api/pnrs", agent, Map.of(
                "passengers", List.of(
                        Map.of("type", "ADT", "firstName", "Ada", "lastName", "Lovelace"),
                        Map.of("type", "ADT", "firstName", "Charles", "lastName", "Babbage"),
                        Map.of("type", "INF", "firstName", "Byron", "lastName", "Lovelace")),
                "contactEmail", "ada@example.com",
                "segments", Map.of(
                        "air", airInputs(offer),
                        "hotels", List.of(Map.of("roomTypeId", hotel.get("roomTypeId").asLong(), "checkIn", TOMORROW.toString(),
                                "checkOut", TOMORROW.plusDays(2).toString(), "rooms", 1)),
                        "cars", List.of(Map.of("vendor", car.get("vendor").asText(), "location", "LHR",
                                "sipp", car.get("sipp").asText(), "pickupDate", TOMORROW.toString(), "days", 2)))));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode pnr = created.getBody();
        String locator = pnr.get("recordLocator").asText();

        assertThat(locator).matches("[A-Z2-9]{6}");
        assertThat(pnr.get("status").asText()).isEqualTo("HELD");
        assertThat(pnr.get("air").get(0).get("seats").asInt()).isEqualTo(2); // the infant sits on a lap
        assertThat(seatsLeft(segment)).isEqualTo(seatsBefore - 2);
        assertThat(queue("TICKETING")).contains(locator);

        JsonNode ticketed = ok(call(HttpMethod.POST, "/api/pnrs/" + locator + "/ticket", agent,
                Map.of("formOfPayment", "CARD", "cardLast4", "4242")));
        assertThat(ticketed.get("status").asText()).isEqualTo("TICKETED");
        assertThat(ticketed.get("tickets").size()).isEqualTo(3);
        assertThat(ticketed.get("tickets").get(0).get("number").asText()).hasSize(13);
        assertThat(ticketed.get("tickets").get(0).get("formOfPayment").asText()).isEqualTo("CARD XXXX4242");
        assertThat(queue("TICKETING")).doesNotContain(locator);

        JsonNode cancelled = ok(call(HttpMethod.POST, "/api/pnrs/" + locator + "/cancel", agent, null));
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
        cancelled.get("tickets").forEach(t -> {
            assertThat(t.get("status").asText()).isEqualTo("VOIDED");
            assertThat(t.get("refundAmount").decimalValue()).isEqualByComparingTo(t.get("total").decimalValue());
        });
        assertThat(seatsLeft(segment)).isEqualTo(seatsBefore);
        assertThat(queue("REFUNDS")).contains(locator);
    }

    @Test
    void refundsFollowFareRulesAfterTheVoidWindow() {
        JsonNode offer = firstOffer("JFK", "MIA");
        String locator = book(offer, 1).get("recordLocator").asText();
        ok(call(HttpMethod.POST, "/api/pnrs/" + locator + "/ticket", agent, Map.of("formOfPayment", "CASH")));
        jdbc.update("update ticket set issued_at = dateadd('DAY', -2, issued_at) where pnr_id = (select id from pnr where record_locator = ?)", locator);

        JsonNode ticket = ok(call(HttpMethod.POST, "/api/pnrs/" + locator + "/cancel", agent, null)).get("tickets").get(0);
        String bookingClass = offer.get("segments").get(0).get("bookingClass").asText();
        boolean nonRefundable = bookingClass.equals("K") || bookingClass.equals("Q");

        assertThat(ticket.get("status").asText()).isEqualTo("REFUNDED");
        if (nonRefundable) {
            assertThat(ticket.get("refundAmount").decimalValue()).isEqualByComparingTo(ticket.get("taxes").decimalValue());
        } else {
            assertThat(ticket.get("refundAmount").decimalValue()).isLessThanOrEqualTo(ticket.get("total").decimalValue());
        }
    }

    @Test
    void cancellingOneSegmentReleasesOnlyThatSegment() {
        JsonNode offer = firstConnection("MEX", "LHR");
        JsonNode pnr = book(offer, 1);
        String locator = pnr.get("recordLocator").asText();
        long firstSegmentId = pnr.get("air").get(0).get("id").asLong();

        JsonNode after = ok(call(HttpMethod.DELETE, "/api/pnrs/" + locator + "/segments/AIR/" + firstSegmentId, agent, null));

        assertThat(after.get("air").get(0).get("status").asText()).isEqualTo("XX");
        assertThat(after.get("air").get(1).get("status").asText()).isEqualTo("HK");
        assertThat(after.at("/airPrice/grandTotal").decimalValue())
                .isLessThan(pnr.at("/airPrice/grandTotal").decimalValue());
    }

    @Test
    void soldOutClassIsRejected() {
        JsonNode offer = firstOffer("ORD", "JFK");
        JsonNode segment = offer.get("segments").get(0);
        setSeats(segment, 1);

        ResponseEntity<JsonNode> response = call(HttpMethod.POST, "/api/pnrs", agent, pnrRequest(offer, 2));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("detail").asText()).contains("sold out");
        assertThat(seatsLeft(segment)).isEqualTo(1); // nothing was taken
    }

    @Test
    void concurrentBookingsNeverOversell() throws Exception {
        JsonNode offer = firstOffer("ATL", "DFW");
        JsonNode segment = offer.get("segments").get(0);
        setSeats(segment, 5);
        Map<String, Object> request = pnrRequest(offer, 1);

        List<HttpStatus> results = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<HttpStatus>> attempts = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                attempts.add(executor.submit(() ->
                        HttpStatus.valueOf(call(HttpMethod.POST, "/api/pnrs", agent, request).getStatusCode().value())));
            }
            for (Future<HttpStatus> attempt : attempts) {
                results.add(attempt.get());
            }
        }

        assertThat(results).filteredOn(s -> s == HttpStatus.CREATED).hasSize(5);
        assertThat(results).filteredOn(s -> s == HttpStatus.CONFLICT).hasSize(35);
        assertThat(seatsLeft(segment)).isZero();
    }

    @Test
    void unticketedHoldsExpireAndGiveSeatsBack() {
        JsonNode offer = firstOffer("SFO", "LAX");
        JsonNode segment = offer.get("segments").get(0);
        int before = seatsLeft(segment);
        String locator = book(offer, 2).get("recordLocator").asText();
        assertThat(seatsLeft(segment)).isEqualTo(before - 2);

        jdbc.update("update pnr set ticketing_deadline = dateadd('MINUTE', -1, current_timestamp) where record_locator = ?", locator);
        expiryJob.expireHolds();

        JsonNode pnr = ok(call(HttpMethod.GET, "/api/pnrs/" + locator, agent, null));
        assertThat(pnr.get("status").asText()).isEqualTo("EXPIRED");
        assertThat(seatsLeft(segment)).isEqualTo(before);
        assertThat(queue("EXPIRED")).contains(locator);
        assertThat(call(HttpMethod.POST, "/api/pnrs/" + locator + "/ticket", agent, Map.of("formOfPayment", "CASH"))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void agenciesOnlySeeTheirOwnPnrs() {
        String locator = book(firstOffer("JFK", "BOS"), 1).get("recordLocator").asText();

        String otherAgency = token("mexagent", "agent123");
        assertThat(call(HttpMethod.GET, "/api/pnrs/" + locator, otherAgency, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(HttpMethod.GET, "/api/pnrs/" + locator, token("agent2", "agent123"), null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.GET, "/api/pnrs/" + locator, token("admin", "admin123"), null).getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode found = ok(call(HttpMethod.GET, "/api/pnrs?lastName=lovelace&status=HELD", agent, null));
        assertThat(found.get("content").findValuesAsText("recordLocator")).contains(locator);
    }

    // ---------------------------------------------------------------- helpers

    private String token(String username, String password) {
        JsonNode body = http.postForObject("/api/auth/login", Map.of("username", username, "password", password), JsonNode.class);
        return body.get("token").asText();
    }

    private ResponseEntity<JsonNode> call(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return http.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private static JsonNode ok(ResponseEntity<JsonNode> response) {
        assertThat(response.getStatusCode().is2xxSuccessful()).as(String.valueOf(response.getBody())).isTrue();
        return response.getBody();
    }

    /** First nonstop with seats for at least two travellers, so tests can book one or two. */
    private JsonNode firstOffer(String origin, String destination) {
        JsonNode offers = ok(call(HttpMethod.GET, "/api/shopping/air?origin=" + origin + "&destination=" + destination
                + "&date=" + TOMORROW + "&adults=2", agent, null)).get("outbound");
        for (JsonNode offer : offers) {
            if (offer.get("stops").asInt() == 0) {
                return offer;
            }
        }
        throw new AssertionError("No nonstop " + origin + "-" + destination);
    }

    private JsonNode firstConnection(String origin, String destination) {
        return ok(call(HttpMethod.GET, "/api/shopping/air?origin=" + origin + "&destination=" + destination
                + "&date=" + TOMORROW, agent, null)).get("outbound").get(0);
    }

    private static List<Map<String, Object>> airInputs(JsonNode offer) {
        List<Map<String, Object>> inputs = new ArrayList<>();
        offer.get("segments").forEach(s -> inputs.add(Map.of("flightId", s.get("flightId").asLong(),
                "bookingClass", s.get("bookingClass").asText())));
        return inputs;
    }

    private static Map<String, Object> pnrRequest(JsonNode offer, int adults) {
        List<Map<String, Object>> passengers = new ArrayList<>();
        for (int i = 0; i < adults; i++) {
            passengers.add(Map.of("type", "ADT", "firstName", "Traveller" + (char) ('A' + i), "lastName", "Lovelace"));
        }
        return Map.of("passengers", passengers, "segments", Map.of("air", airInputs(offer)));
    }

    private JsonNode book(JsonNode offer, int adults) {
        var response = call(HttpMethod.POST, "/api/pnrs", agent, pnrRequest(offer, adults));
        assertThat(response.getStatusCode()).as(String.valueOf(response.getBody())).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private int seatsLeft(JsonNode segment) {
        return jdbc.queryForObject("select seats_available from fare_inventory where flight_id = ? and booking_class = ?",
                Integer.class, segment.get("flightId").asLong(), segment.get("bookingClass").asText());
    }

    private void setSeats(JsonNode segment, int seats) {
        jdbc.update("update fare_inventory set seats_available = ? where flight_id = ? and booking_class = ?",
                seats, segment.get("flightId").asLong(), segment.get("bookingClass").asText());
    }

    private List<String> queue(String name) {
        return ok(call(HttpMethod.GET, "/api/queues/" + name, agent, null)).findValuesAsText("recordLocator");
    }
}
