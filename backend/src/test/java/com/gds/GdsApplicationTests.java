package com.gds;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.fasterxml.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GdsApplicationTests {

    @Autowired
    TestRestTemplate http;

    @Test
    void seedsInventoryForEveryProduct() {
        JsonNode stats = http.getForObject("/api/system/stats", JsonNode.class);

        assertThat(stats.at("/rows/airport").asLong()).isEqualTo(44);
        assertThat(stats.at("/rows/flight").asLong()).isPositive();
        assertThat(stats.at("/rows/fare_inventory").asLong()).isGreaterThan(stats.at("/rows/flight").asLong());
        assertThat(stats.at("/rows/room_inventory").asLong()).isPositive();
        assertThat(stats.at("/rows/car_inventory").asLong()).isPositive();
    }

    @Test
    void requestsRunOnVirtualThreads() {
        JsonNode stats = http.getForObject("/api/system/stats", JsonNode.class);

        assertThat(stats.at("/runtime/virtualThread").asBoolean()).isTrue();
    }

    @Test
    void returnsFlightInventoryWithBookingClasses() {
        JsonNode flights = http.getForObject("/api/inventory/flights?origin=JFK&destination=LHR&date=" + LocalDate.now(),
                JsonNode.class);

        assertThat(flights.size()).isGreaterThanOrEqualTo(2); // NA and BW both fly JFK-LHR
        JsonNode first = flights.get(0);
        assertThat(first.get("arrival").asText()).isGreaterThan(first.get("departure").asText());
        assertThat(first.get("classes").size()).isGreaterThanOrEqualTo(7);
    }

    @Test
    void returnsHotelAndCarInventory() {
        String today = LocalDate.now().toString();
        JsonNode hotels = http.getForObject("/api/inventory/hotels?city=CDG&date=" + today, JsonNode.class);
        JsonNode cars = http.getForObject("/api/inventory/cars?location=MEX&date=" + today, JsonNode.class);

        assertThat(hotels.size()).isEqualTo(2);
        assertThat(hotels.get(0).get("rooms").size()).isEqualTo(4);
        assertThat(cars.size()).isEqualTo(ReferenceCounts.CAR_VENDORS * ReferenceCounts.CAR_CATEGORIES);
    }

    static final class ReferenceCounts {
        static final int CAR_VENDORS = 5;
        static final int CAR_CATEGORIES = 9;
    }
}
