package com.gds.seed;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.gds.config.GdsProperties;
import com.gds.seed.ReferenceData.AirlineSeed;
import com.gds.seed.ReferenceData.AirportSeed;
import com.gds.seed.ReferenceData.CarSeed;
import com.gds.seed.ReferenceData.ChainSeed;

/**
 * Generates the mock supplier inventory on first start: airports and airlines, dated flight schedules
 * with booking-class seat counts, hotels with nightly room inventory, and car rental fleets.
 * Uses a fixed random seed so every machine gets the same data. Skips when the database already has data.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final int BATCH = 2_000;
    private static final String CURRENCY = "USD";

    /** Booking classes from most to least expensive, with fare multiplier and share of the cabin's seats. */
    private record ClassSpec(String code, String cabin, double multiplier, double seatShare) {
    }

    private static final List<ClassSpec> FIRST = List.of(new ClassSpec("F", "FIRST", 7.0, 1.0));
    private static final List<ClassSpec> BUSINESS = List.of(
            new ClassSpec("J", "BUSINESS", 4.2, 0.6), new ClassSpec("C", "BUSINESS", 3.4, 0.4));
    private static final List<ClassSpec> PREMIUM = List.of(new ClassSpec("W", "PREMIUM_ECONOMY", 1.9, 1.0));
    private static final List<ClassSpec> ECONOMY = List.of(
            new ClassSpec("Y", "ECONOMY", 1.45, 0.10), new ClassSpec("B", "ECONOMY", 1.20, 0.15),
            new ClassSpec("M", "ECONOMY", 1.00, 0.20), new ClassSpec("H", "ECONOMY", 0.85, 0.20),
            new ClassSpec("K", "ECONOMY", 0.70, 0.20), new ClassSpec("Q", "ECONOMY", 0.55, 0.15));

    /** Seat counts per cabin: first, business, premium economy, economy. */
    private record Aircraft(String type, int first, int business, int premium, int economy) {
    }

    private static final Aircraft NARROW = new Aircraft("A320", 0, 12, 0, 150);
    private static final Aircraft NARROW_LONG = new Aircraft("B738", 0, 16, 0, 156);
    private static final Aircraft WIDE = new Aircraft("B789", 0, 30, 21, 220);
    private static final Aircraft WIDE_LARGE = new Aircraft("B77W", 8, 42, 24, 280);

    private record ClassRow(long flightId, ClassSpec spec, BigDecimal fare, int total, int available) {
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final GdsProperties props;

    public DataSeeder(JdbcTemplate jdbc, TransactionTemplate tx, GdsProperties props) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.seed().enabled()) {
            return;
        }
        Long airports = jdbc.queryForObject("select count(*) from airport", Long.class);
        if (airports != null && airports > 0) {
            log.info("Inventory already present ({} airports), skipping seed", airports);
            return;
        }
        long started = System.currentTimeMillis();
        LocalDate firstDay = LocalDate.now();
        int days = props.seed().days();
        Random random = new Random(props.seed().randomSeed());

        tx.executeWithoutResult(status -> {
            seedReference();
            long flights = seedFlights(random, firstDay, days);
            long rooms = seedHotels(random, firstDay, days);
            long cars = seedCars(random, firstDay, days);
            log.info("Seeded {} flights, {} room-nights and {} car inventory rows for {} days in {} ms",
                    flights, rooms, cars, days, System.currentTimeMillis() - started);
        });
    }

    private void seedReference() {
        jdbc.batchUpdate("insert into airport (code, name, city, country, timezone, latitude, longitude) values (?,?,?,?,?,?,?)",
                ReferenceData.AIRPORTS.stream()
                        .map(a -> new Object[] {a.code(), a.name(), a.city(), a.country(), a.timezone(), a.lat(), a.lon()})
                        .toList());
        jdbc.batchUpdate("insert into airline (code, name, country, hub) values (?,?,?,?)",
                ReferenceData.AIRLINES.stream()
                        .map(a -> new Object[] {a.code(), a.name(), a.country(), a.hub()})
                        .toList());
    }

    // ---------------------------------------------------------------- flights

    private long seedFlights(Random random, LocalDate firstDay, int days) {
        Map<String, AirportSeed> airports = new HashMap<>();
        ReferenceData.AIRPORTS.forEach(a -> airports.put(a.code(), a));

        List<Object[]> flightRows = new ArrayList<>(BATCH);
        List<ClassRow> classRows = new ArrayList<>(BATCH);
        long flightId = 0;
        long fareId = 0;

        for (AirlineSeed airline : ReferenceData.AIRLINES) {
            int flightNumber = 100;
            for (String[] leg : legs(airline)) {
                AirportSeed from = airports.get(leg[0]);
                AirportSeed to = airports.get(leg[1]);
                double km = distanceKm(from, to);
                int blockMinutes = (int) Math.round(km / 820.0 * 60 + 35);
                int frequency = km < 1500 ? 4 : km < 5000 ? 2 : 1;
                Aircraft aircraft = km < 2500 ? NARROW : km < 5000 ? NARROW_LONG : km < 9000 ? WIDE : WIDE_LARGE;
                List<LocalTime> slots = departureSlots(random, frequency);
                double economyBase = 45 + km * 0.075;

                for (LocalTime slot : slots) {
                    int number = flightNumber++;
                    for (int d = 0; d < days; d++) {
                        LocalDate date = firstDay.plusDays(d);
                        ZonedDateTime departure = ZonedDateTime.of(date, slot, ZoneId.of(from.timezone()));
                        ZonedDateTime arrival = departure.plusMinutes(blockMinutes).withZoneSameInstant(ZoneId.of(to.timezone()));
                        long id = ++flightId;
                        flightRows.add(new Object[] {id, airline.code(), number, from.code(), to.code(), Date.valueOf(date),
                                Timestamp.valueOf(departure.toLocalDateTime()), Timestamp.valueOf(arrival.toLocalDateTime()),
                                blockMinutes, aircraft.type()});

                        double demand = demand(random, date, d, days);
                        for (ClassRow row : bookingClasses(random, id, aircraft, economyBase, demand)) {
                            classRows.add(row);
                        }
                        if (flightRows.size() >= BATCH) {
                            insertFlights(flightRows);
                        }
                        if (classRows.size() >= BATCH) {
                            fareId = insertClasses(classRows, fareId);
                        }
                    }
                }
            }
        }
        insertFlights(flightRows);
        insertClasses(classRows, fareId);
        return flightId;
    }

    /** Hub-and-spoke network: every destination is served out of the hub and back. */
    private static List<String[]> legs(AirlineSeed airline) {
        Set<String> destinations = new LinkedHashSet<>(airline.destinations());
        destinations.remove(airline.hub());
        List<String[]> legs = new ArrayList<>();
        for (String destination : destinations) {
            legs.add(new String[] {airline.hub(), destination});
            legs.add(new String[] {destination, airline.hub()});
        }
        return legs;
    }

    private static List<LocalTime> departureSlots(Random random, int frequency) {
        List<LocalTime> slots = new ArrayList<>();
        int window = (22 - 6) * 60 / frequency;
        for (int i = 0; i < frequency; i++) {
            int minute = 6 * 60 + i * window + random.nextInt(Math.max(window - 60, 1));
            slots.add(LocalTime.of(minute / 60, (minute % 60) / 5 * 5));
        }
        return slots;
    }

    /** 0..1 share of seats already sold: higher close to departure and on Fridays and Sundays. */
    private static double demand(Random random, LocalDate date, int dayIndex, int days) {
        double proximity = 1.0 - (double) dayIndex / Math.max(days, 1);
        double weekday = switch (date.getDayOfWeek()) {
            case FRIDAY, SUNDAY -> 0.15;
            case SATURDAY, TUESDAY -> -0.1;
            default -> 0.0;
        };
        return clamp(0.15 + proximity * 0.55 + weekday + random.nextGaussian() * 0.12, 0.0, 0.98);
    }

    private static List<ClassRow> bookingClasses(Random random, long flightId, Aircraft aircraft, double economyBase, double demand) {
        List<ClassRow> rows = new ArrayList<>();
        addCabin(rows, random, flightId, FIRST, aircraft.first(), economyBase, demand * 0.7);
        addCabin(rows, random, flightId, BUSINESS, aircraft.business(), economyBase, demand * 0.85);
        addCabin(rows, random, flightId, PREMIUM, aircraft.premium(), economyBase, demand * 0.9);
        addCabin(rows, random, flightId, ECONOMY, aircraft.economy(), economyBase, demand);
        return rows;
    }

    /** Cheaper classes sell out first, which is how nested booking classes behave as a flight fills. */
    private static void addCabin(List<ClassRow> rows, Random random, long flightId, List<ClassSpec> classes,
                                 int seats, double economyBase, double demand) {
        if (seats == 0) {
            return;
        }
        for (int i = 0; i < classes.size(); i++) {
            ClassSpec spec = classes.get(i);
            int total = Math.max(1, (int) Math.round(seats * spec.seatShare()));
            double cheapness = classes.size() == 1 ? 0.5 : (double) i / (classes.size() - 1);
            double sold = clamp(demand * (0.6 + cheapness * 0.8) + random.nextGaussian() * 0.08, 0, 1);
            int available = (int) Math.round(total * (1 - sold));
            BigDecimal fare = BigDecimal.valueOf(economyBase * spec.multiplier() * (0.95 + random.nextDouble() * 0.1))
                    .setScale(0, RoundingMode.HALF_UP).setScale(2);
            rows.add(new ClassRow(flightId, spec, fare, total, available));
        }
    }

    private void insertFlights(List<Object[]> rows) {
        if (rows.isEmpty()) {
            return;
        }
        jdbc.batchUpdate("""
                insert into flight (id, airline_code, flight_number, origin, destination, departure_date,
                                    departure, arrival, duration_minutes, aircraft)
                values (?,?,?,?,?,?,?,?,?,?)""", rows);
        rows.clear();
    }

    private long insertClasses(List<ClassRow> rows, long lastId) {
        if (rows.isEmpty()) {
            return lastId;
        }
        List<Object[]> args = new ArrayList<>(rows.size());
        for (ClassRow r : rows) {
            args.add(new Object[] {++lastId, r.flightId(), r.spec().code(), r.spec().cabin(), r.fare(), CURRENCY,
                    r.total(), r.available(), 0});
        }
        jdbc.batchUpdate("""
                insert into fare_inventory (id, flight_id, booking_class, cabin, base_fare, currency,
                                            seats_total, seats_available, version)
                values (?,?,?,?,?,?,?,?,?)""", args);
        rows.clear();
        return lastId;
    }

    // ---------------------------------------------------------------- hotels

    private long seedHotels(Random random, LocalDate firstDay, int days) {
        String[][] roomTypes = {
                {"STD", "Standard room, 1 queen bed", "2", "1.0"},
                {"DLX", "Deluxe room, 1 king bed", "2", "1.35"},
                {"FAM", "Family room, 2 double beds", "4", "1.5"},
                {"STE", "Junior suite with lounge", "3", "2.2"}};

        List<Object[]> hotels = new ArrayList<>();
        List<Object[]> types = new ArrayList<>();
        List<Object[]> nights = new ArrayList<>(BATCH);
        long hotelId = 0;
        long roomTypeId = 0;
        long nightId = 0;
        Map<String, Integer> chainCounters = new HashMap<>();

        for (AirportSeed city : ReferenceData.AIRPORTS) {
            for (int h = 0; h < props.seed().hotelsPerCity(); h++) {
                ChainSeed chain = ReferenceData.CHAINS.get(random.nextInt(ReferenceData.CHAINS.size()));
                int stars = chain.minStars() + random.nextInt(chain.maxStars() - chain.minStars() + 1);
                String suffix = ReferenceData.HOTEL_SUFFIXES.get(random.nextInt(ReferenceData.HOTEL_SUFFIXES.size()));
                int sequence = chainCounters.merge(chain.code(), 1, Integer::sum);
                long id = ++hotelId;
                hotels.add(new Object[] {id, "%s%04d".formatted(chain.code(), sequence), chain.code(),
                        "%s %s %s".formatted(chain.brand(), city.city(), suffix), city.code(), stars,
                        "%d %s, %s".formatted(1 + random.nextInt(900),
                                ReferenceData.STREETS.get(random.nextInt(ReferenceData.STREETS.size())), city.city())});

                double base = (35 + stars * 32) * city.costFactor();
                int capacity = 40 + random.nextInt(160);
                for (String[] type : roomTypes) {
                    long typeId = ++roomTypeId;
                    BigDecimal rate = BigDecimal.valueOf(base * Double.parseDouble(type[3]) * (0.9 + random.nextDouble() * 0.2))
                            .setScale(0, RoundingMode.HALF_UP).setScale(2);
                    types.add(new Object[] {typeId, id, type[0], type[1], Integer.parseInt(type[2]), rate, CURRENCY});
                    int rooms = Math.max(2, (int) (capacity * switch (type[0]) {
                        case "STD" -> 0.5;
                        case "DLX" -> 0.25;
                        case "FAM" -> 0.15;
                        default -> 0.1;
                    }));
                    for (int d = 0; d < days; d++) {
                        LocalDate date = firstDay.plusDays(d);
                        boolean weekend = date.getDayOfWeek() == DayOfWeek.FRIDAY || date.getDayOfWeek() == DayOfWeek.SATURDAY;
                        double occupancy = clamp(0.55 + (weekend ? 0.2 : 0) + random.nextGaussian() * 0.15, 0, 1);
                        nights.add(new Object[] {++nightId, typeId, Date.valueOf(date), rooms,
                                (int) Math.round(rooms * (1 - occupancy)), 0});
                        if (nights.size() >= BATCH) {
                            insertRoomNights(nights);
                        }
                    }
                }
            }
        }
        jdbc.batchUpdate("insert into hotel (id, property_code, chain_code, name, city_code, stars, address) values (?,?,?,?,?,?,?)", hotels);
        jdbc.batchUpdate("insert into room_type (id, hotel_id, code, description, max_occupancy, nightly_rate, currency) values (?,?,?,?,?,?,?)", types);
        insertRoomNights(nights);
        return nightId;
    }

    private void insertRoomNights(List<Object[]> rows) {
        if (rows.isEmpty()) {
            return;
        }
        jdbc.batchUpdate("""
                insert into room_inventory (id, room_type_id, stay_date, rooms_total, rooms_available, version)
                values (?,?,?,?,?,?)""", rows);
        rows.clear();
    }

    // ---------------------------------------------------------------- cars

    private long seedCars(Random random, LocalDate firstDay, int days) {
        List<Object[]> rows = new ArrayList<>(BATCH);
        long id = 0;
        for (AirportSeed location : ReferenceData.AIRPORTS) {
            for (String vendor : ReferenceData.CAR_VENDORS) {
                double vendorFactor = 0.85 + random.nextDouble() * 0.3;
                for (CarSeed car : ReferenceData.CARS) {
                    int fleet = 3 + random.nextInt(25);
                    BigDecimal rate = BigDecimal.valueOf(car.baseRate() * location.costFactor() * vendorFactor)
                            .setScale(2, RoundingMode.HALF_UP);
                    for (int d = 0; d < days; d++) {
                        double utilisation = clamp(0.5 + random.nextGaussian() * 0.2, 0, 1);
                        rows.add(new Object[] {++id, vendor, location.code(), Date.valueOf(firstDay.plusDays(d)),
                                car.sipp(), car.model(), rate, CURRENCY, fleet, (int) Math.round(fleet * (1 - utilisation)), 0});
                        if (rows.size() >= BATCH) {
                            insertCars(rows);
                        }
                    }
                }
            }
        }
        insertCars(rows);
        return id;
    }

    private void insertCars(List<Object[]> rows) {
        if (rows.isEmpty()) {
            return;
        }
        jdbc.batchUpdate("""
                insert into car_inventory (id, vendor_code, location_code, pickup_date, sipp_code, sample_model,
                                           daily_rate, currency, units_total, units_available, version)
                values (?,?,?,?,?,?,?,?,?,?,?)""", rows);
        rows.clear();
    }

    // ---------------------------------------------------------------- helpers

    private static double distanceKm(AirportSeed a, AirportSeed b) {
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLon = Math.toRadians(b.lon() - a.lon());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.lat())) * Math.cos(Math.toRadians(b.lat())) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
