package com.gds.booking;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gds.booking.PnrRequests.AirSegmentInput;
import com.gds.booking.PnrRequests.CarSegmentInput;
import com.gds.booking.PnrRequests.ContactUpdate;
import com.gds.booking.PnrRequests.CreatePnr;
import com.gds.booking.PnrRequests.HotelSegmentInput;
import com.gds.booking.PnrRequests.PassengerInput;
import com.gds.booking.PnrRequests.SegmentType;
import com.gds.booking.PnrRequests.Segments;
import com.gds.booking.PnrRequests.TicketRequest;
import com.gds.car.CarInventory;
import com.gds.car.CarInventoryRepository;
import com.gds.common.BadRequestException;
import com.gds.common.ConflictException;
import com.gds.common.NotFoundException;
import com.gds.config.GdsProperties;
import com.gds.flight.FareInventory;
import com.gds.flight.FareInventoryRepository;
import com.gds.flight.Flight;
import com.gds.flight.FlightRepository;
import com.gds.hotel.Hotel;
import com.gds.hotel.HotelRepository;
import com.gds.hotel.RoomType;
import com.gds.hotel.RoomTypeRepository;
import com.gds.pricing.FareRules;
import com.gds.pricing.PassengerType;
import com.gds.pricing.PricingService;
import com.gds.pricing.PricingService.AirPrice;
import com.gds.pricing.PricingService.PaxPrice;
import com.gds.pricing.PricingService.SegmentFare;
import com.gds.queue.QueueName;
import com.gds.queue.QueueService;
import com.gds.security.CurrentAgent;

/**
 * The PNR lifecycle: create with inventory held, modify while held, ticket before the deadline,
 * cancel with refunds, and expire automatically when the deadline passes.
 * Every operation is one transaction, so inventory, PNR and queues always change together.
 */
@Service
public class PnrService {

    private static final String SYSTEM = "SYSTEM";
    private static final Duration VOID_WINDOW = Duration.ofHours(24);

    private final PnrRepository pnrs;
    private final FlightRepository flights;
    private final FareInventoryRepository fares;
    private final HotelRepository hotels;
    private final RoomTypeRepository roomTypes;
    private final CarInventoryRepository cars;
    private final InventoryAllocator allocator;
    private final PricingService pricing;
    private final QueueService queues;
    private final RecordLocatorGenerator locators;
    private final TicketNumberGenerator ticketNumbers;
    private final GdsProperties props;

    public PnrService(PnrRepository pnrs, FlightRepository flights, FareInventoryRepository fares, HotelRepository hotels,
                      RoomTypeRepository roomTypes, CarInventoryRepository cars, InventoryAllocator allocator,
                      PricingService pricing, QueueService queues, RecordLocatorGenerator locators,
                      TicketNumberGenerator ticketNumbers, GdsProperties props) {
        this.pnrs = pnrs;
        this.flights = flights;
        this.fares = fares;
        this.hotels = hotels;
        this.roomTypes = roomTypes;
        this.cars = cars;
        this.allocator = allocator;
        this.pricing = pricing;
        this.queues = queues;
        this.locators = locators;
        this.ticketNumbers = ticketNumbers;
        this.props = props;
    }

    // ---------------------------------------------------------------- create and modify

    @Transactional
    public PnrView create(CreatePnr request, CurrentAgent agent) {
        if (request.segments().isEmpty()) {
            throw new BadRequestException("A PNR needs at least one air, hotel or car segment");
        }
        Map<PassengerType, Integer> counts = countByType(request.passengers());
        if (counts.get(PassengerType.INF) > counts.get(PassengerType.ADT)) {
            throw new BadRequestException("Each infant must travel with an adult");
        }
        if (counts.get(PassengerType.ADT) + counts.get(PassengerType.CHD) == 0) {
            throw new BadRequestException("A PNR needs at least one adult or child");
        }

        Instant now = Instant.now();
        Pnr pnr = new Pnr(locators.next(), agent.agencyId(), agent.pcc(), agent.username(), now,
                now.plus(props.booking().holdTime()), request.contactEmail(), request.contactPhone());
        request.passengers().forEach(p -> pnr.getPassengers().add(
                new Passenger(p.type(), p.firstName().trim(), p.lastName().trim(), p.dateOfBirth())));
        pnr.log(agent.username(), "CREATED", "%d passenger(s), held until %s".formatted(
                pnr.getPassengers().size(), pnr.getTicketingDeadline()));
        addSegments(pnr, request.segments(), agent);
        pnrs.save(pnr);
        queues.place(pnr.getAgencyId(), QueueName.TICKETING, pnr.getId(), pnr.getRecordLocator(),
                "Ticket by " + pnr.getTicketingDeadline());
        return view(pnr);
    }

    @Transactional
    public PnrView addSegments(String locator, Segments segments, CurrentAgent agent) {
        Pnr pnr = held(locator, agent);
        if (segments.isEmpty()) {
            throw new BadRequestException("No segments to add");
        }
        addSegments(pnr, segments, agent);
        return view(pnr);
    }

    @Transactional
    public PnrView cancelSegment(String locator, SegmentType type, Long segmentId, CurrentAgent agent) {
        Pnr pnr = held(locator, agent);
        switch (type) {
            case AIR -> {
                AirSegment segment = find(pnr.getAirSegments(), AirSegment::getId, segmentId, AirSegment::isActive);
                allocator.releaseSeats(segment);
                segment.cancel();
                pnr.log(agent.username(), "SEGMENT_XX", segment.getFlight() + " " + segment.getOrigin() + segment.getDestination());
            }
            case HOTEL -> {
                HotelSegment segment = find(pnr.getHotelSegments(), HotelSegment::getId, segmentId, HotelSegment::isActive);
                allocator.releaseRooms(segment);
                segment.cancel();
                pnr.log(agent.username(), "SEGMENT_XX", segment.getPropertyCode() + " " + segment.getCheckIn());
            }
            case CAR -> {
                CarSegment segment = find(pnr.getCarSegments(), CarSegment::getId, segmentId, CarSegment::isActive);
                allocator.releaseCar(segment);
                segment.cancel();
                pnr.log(agent.username(), "SEGMENT_XX", segment.getVendorCode() + " " + segment.getSippCode());
            }
        }
        return view(pnr);
    }

    @Transactional
    public PnrView updateContact(String locator, ContactUpdate update, CurrentAgent agent) {
        Pnr pnr = load(locator, agent);
        pnr.setContact(update.contactEmail(), update.contactPhone());
        pnr.log(agent.username(), "CONTACT", "Contact details updated");
        return view(pnr);
    }

    private void addSegments(Pnr pnr, Segments segments, CurrentAgent agent) {
        int seats = (int) pnr.getPassengers().stream().filter(p -> p.getType().occupiesSeat()).count();
        for (AirSegmentInput input : segments.airOrEmpty()) {
            FareInventory fare = fares.findByFlightIdAndBookingClass(input.flightId(), input.bookingClass())
                    .orElseThrow(() -> new NotFoundException("No class " + input.bookingClass() + " on flight " + input.flightId()));
            Flight flight = flights.findById(input.flightId()).orElseThrow();
            if (flight.getDeparture().isBefore(LocalDateTime.now())) {
                throw new ConflictException(flight.getAirlineCode() + flight.getFlightNumber() + " has already departed");
            }
            AirSegment segment = new AirSegment(fare.getId(), flight.getId(), flight.getAirlineCode(),
                    flight.getAirlineCode() + flight.getFlightNumber(), flight.getOrigin(), flight.getDestination(),
                    flight.getDeparture(), flight.getArrival(), fare.getBookingClass(), fare.getCabin(),
                    FareRules.forClass(fare.getBookingClass()).fareBasis(fare.getBookingClass()), seats, fare.getBaseFare());
            allocator.holdSeats(segment);
            pnr.getAirSegments().add(segment);
            pnr.log(agent.username(), "SEGMENT_HK", "%s %s%s %s class %s, %d seat(s)".formatted(segment.getFlight(),
                    segment.getOrigin(), segment.getDestination(), segment.getDeparture(), segment.getBookingClass(), seats));
        }
        for (HotelSegmentInput input : segments.hotelsOrEmpty()) {
            RoomType room = roomTypes.findById(input.roomTypeId())
                    .orElseThrow(() -> new NotFoundException("No room type " + input.roomTypeId()));
            Hotel hotel = hotels.findById(room.getHotelId()).orElseThrow();
            var price = pricing.priceHotel(room.getNightlyRate(),
                    nights(input.checkIn(), input.checkOut()), input.rooms(), room.getCurrency());
            HotelSegment segment = new HotelSegment(hotel.getId(), room.getId(), hotel.getPropertyCode(), hotel.getName(),
                    hotel.getCityCode(), room.getCode(), input.checkIn(), input.checkOut(), input.rooms(),
                    room.getNightlyRate(), price.total(), RecordLocatorGenerator.confirmation("H"));
            allocator.holdRooms(segment);
            pnr.getHotelSegments().add(segment);
            pnr.log(agent.username(), "SEGMENT_HK", "%s %s %s-%s x%d".formatted(hotel.getPropertyCode(), room.getCode(),
                    input.checkIn(), input.checkOut(), input.rooms()));
        }
        for (CarSegmentInput input : segments.carsOrEmpty()) {
            CarInventory car = cars.findByVendorCodeAndLocationCodeAndSippCodeAndPickupDate(
                            input.vendor(), input.location(), input.sipp(), input.pickupDate())
                    .orElseThrow(() -> new NotFoundException("No %s %s at %s on %s".formatted(
                            input.vendor(), input.sipp(), input.location(), input.pickupDate())));
            var price = pricing.priceCar(car.getDailyRate(), input.days(), car.getCurrency());
            CarSegment segment = new CarSegment(car.getVendorCode(), car.getLocationCode(), car.getSippCode(),
                    car.getSampleModel(), input.pickupDate(), input.pickupDate().plusDays(input.days()),
                    car.getDailyRate(), price.total(), RecordLocatorGenerator.confirmation("C"));
            allocator.holdCar(segment);
            pnr.getCarSegments().add(segment);
            pnr.log(agent.username(), "SEGMENT_HK", "%s %s %s %s for %d day(s)".formatted(car.getVendorCode(),
                    car.getSippCode(), car.getLocationCode(), input.pickupDate(), input.days()));
        }
    }

    // ---------------------------------------------------------------- ticketing, cancellation, expiry

    @Transactional
    public PnrView ticket(String locator, TicketRequest request, CurrentAgent agent) {
        Pnr pnr = held(locator, agent);
        List<AirSegment> air = pnr.activeAirSegments();
        if (air.isEmpty()) {
            throw new ConflictException("Nothing to ticket: the PNR has no confirmed air segments");
        }
        AirPrice price = airPrice(pnr);
        Map<PassengerType, PaxPrice> byType = price.passengers().stream()
                .collect(Collectors.toMap(PaxPrice::type, p -> p));
        String payment = request.formOfPayment() == PnrRequests.FormOfPayment.CARD && request.cardLast4() != null
                ? "CARD XXXX" + request.cardLast4()
                : request.formOfPayment().name();
        Instant now = Instant.now();
        String validatingAirline = air.getFirst().getAirlineCode();
        for (Passenger passenger : pnr.getPassengers()) {
            PaxPrice line = byType.get(passenger.getType());
            pnr.getTickets().add(new Ticket(ticketNumbers.next(validatingAirline), passenger.getId(),
                    passenger.displayName(), line.baseFare(), line.taxes(), payment, now));
        }
        pnr.setStatus(PnrStatus.TICKETED);
        pnr.setTicketingDeadline(null);
        pnr.log(agent.username(), "TICKETED", "%d ticket(s), %s %s, %s".formatted(
                pnr.getPassengers().size(), price.grandTotal(), price.currency(), payment));
        queues.remove(pnr.getId(), QueueName.TICKETING);
        return view(pnr);
    }

    @Transactional
    public PnrView cancel(String locator, CurrentAgent agent) {
        Pnr pnr = load(locator, agent);
        PnrStatus before = pnr.getStatus();
        if (before == PnrStatus.CANCELLED || before == PnrStatus.EXPIRED) {
            throw new ConflictException("PNR " + locator + " is already " + before);
        }
        List<AirSegment> ticketedSegments = pnr.activeAirSegments();
        releaseAll(pnr);
        pnr.setStatus(PnrStatus.CANCELLED);
        pnr.setTicketingDeadline(null);
        queues.remove(pnr.getId(), QueueName.TICKETING);

        if (before == PnrStatus.TICKETED) {
            BigDecimal refunded = refundTickets(pnr, ticketedSegments);
            pnr.log(agent.username(), "CANCELLED", "Tickets refunded or voided, %s %s back to customer"
                    .formatted(refunded, "USD"));
            queues.place(pnr.getAgencyId(), QueueName.REFUNDS, pnr.getId(), pnr.getRecordLocator(),
                    "Refund " + refunded + " USD");
        } else {
            pnr.log(agent.username(), "CANCELLED", "Held inventory released");
        }
        return view(pnr);
    }

    /** Releases a hold whose ticketing deadline passed. Called by {@link HoldExpiryJob}, one PNR per transaction. */
    @Transactional
    public boolean expire(Long pnrId) {
        Pnr pnr = pnrs.findById(pnrId).orElse(null);
        if (pnr == null || pnr.getStatus() != PnrStatus.HELD || pnr.getTicketingDeadline().isAfter(Instant.now())) {
            return false;
        }
        releaseAll(pnr);
        pnr.setStatus(PnrStatus.EXPIRED);
        pnr.log(SYSTEM, "EXPIRED", "Not ticketed by " + pnr.getTicketingDeadline() + ", inventory released");
        queues.remove(pnr.getId(), QueueName.TICKETING);
        queues.place(pnr.getAgencyId(), QueueName.EXPIRED, pnr.getId(), pnr.getRecordLocator(), "Hold expired");
        return true;
    }

    private void releaseAll(Pnr pnr) {
        for (AirSegment segment : pnr.activeAirSegments()) {
            allocator.releaseSeats(segment);
            segment.cancel();
        }
        for (HotelSegment segment : pnr.getHotelSegments()) {
            if (segment.isActive()) {
                allocator.releaseRooms(segment);
                segment.cancel();
            }
        }
        for (CarSegment segment : pnr.getCarSegments()) {
            if (segment.isActive()) {
                allocator.releaseCar(segment);
                segment.cancel();
            }
        }
    }

    /**
     * Same-day cancellations void the ticket for a full refund. After that the fare rules apply:
     * non-refundable fares give back taxes only, refundable fares give back the total minus each segment's penalty.
     */
    private BigDecimal refundTickets(Pnr pnr, List<AirSegment> segments) {
        Instant now = Instant.now();
        boolean refundable = segments.stream().allMatch(s -> FareRules.forClass(s.getBookingClass()).refundable());
        BigDecimal penalty = segments.stream()
                .map(s -> FareRules.forClass(s.getBookingClass()).cancelPenalty())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = BigDecimal.ZERO;
        for (Ticket ticket : pnr.getTickets()) {
            if (ticket.getStatus() != TicketStatus.ISSUED) {
                continue;
            }
            if (Duration.between(ticket.getIssuedAt(), now).compareTo(VOID_WINDOW) < 0) {
                ticket.voidTicket();
            } else if (refundable) {
                ticket.refund(ticket.getTotal().subtract(penalty).max(ticket.getTaxes()));
            } else {
                ticket.refund(ticket.getTaxes());
            }
            total = total.add(ticket.getRefundAmount());
        }
        return total;
    }

    // ---------------------------------------------------------------- reads

    @Transactional(readOnly = true)
    public PnrView retrieve(String locator, CurrentAgent agent) {
        return view(load(locator, agent));
    }

    @Transactional(readOnly = true)
    public Page<PnrView.Summary> search(CurrentAgent agent, PnrStatus status, String lastName, int page, int size) {
        return pnrs.search(agent.isAdmin() ? null : agent.agencyId(), status,
                        lastName == null || lastName.isBlank() ? null : lastName.trim().toUpperCase(),
                        PageRequest.of(page, Math.min(size, 100)))
                .map(PnrService::summary);
    }

    /** Agents only see their own agency's PNRs; anything else looks like it doesn't exist. */
    @Transactional(readOnly = true)
    public Pnr load(String locator, CurrentAgent agent) {
        return pnrs.findByRecordLocator(locator.toUpperCase())
                .filter(p -> agent.canAccessAgency(p.getAgencyId()))
                .orElseThrow(() -> new NotFoundException("No PNR " + locator));
    }

    private Pnr held(String locator, CurrentAgent agent) {
        Pnr pnr = load(locator, agent);
        if (pnr.getStatus() != PnrStatus.HELD) {
            throw new ConflictException("PNR " + locator + " is " + pnr.getStatus() + ", only held PNRs can be changed");
        }
        if (pnr.getTicketingDeadline().isBefore(Instant.now())) {
            throw new ConflictException("PNR " + locator + " passed its ticketing deadline");
        }
        return pnr;
    }

    // ---------------------------------------------------------------- mapping

    private AirPrice airPrice(Pnr pnr) {
        List<AirSegment> air = pnr.activeAirSegments();
        if (air.isEmpty()) {
            return null;
        }
        Map<PassengerType, Integer> counts = new EnumMap<>(PassengerType.class);
        pnr.getPassengers().forEach(p -> counts.merge(p.getType(), 1, Integer::sum));
        return pricing.priceAir(air.stream().map(s -> new SegmentFare(s.getBookingClass(), s.getBaseFare())).toList(),
                counts, "USD");
    }

    PnrView view(Pnr pnr) {
        AirPrice air = airPrice(pnr);
        BigDecimal hotelTotal = pnr.getHotelSegments().stream().filter(HotelSegment::isActive)
                .map(HotelSegment::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal carTotal = pnr.getCarSegments().stream().filter(CarSegment::isActive)
                .map(CarSegment::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grand = (air == null ? BigDecimal.ZERO : air.grandTotal()).add(hotelTotal).add(carTotal);
        return new PnrView(pnr.getRecordLocator(), pnr.getStatus(), pnr.getPcc(), pnr.getCreatedBy(), pnr.getCreatedAt(),
                pnr.getTicketingDeadline(), pnr.getContactEmail(), pnr.getContactPhone(),
                pnr.getPassengers().stream()
                        .map(p -> new PnrView.PassengerLine(p.getId(), p.getType(), p.displayName(), p.getDateOfBirth()))
                        .toList(),
                pnr.getAirSegments().stream()
                        .map(s -> new PnrView.AirLine(s.getId(), s.getFlight(), s.getOrigin(), s.getDestination(),
                                s.getDeparture(), s.getArrival(), s.getBookingClass(), s.getCabin(), s.getFareBasis(),
                                s.getSeats(), s.getBaseFare(), FareRules.forClass(s.getBookingClass()), s.getStatus()))
                        .toList(),
                pnr.getHotelSegments().stream()
                        .map(h -> new PnrView.HotelLine(h.getId(), h.getPropertyCode(), h.getHotelName(), h.getCityCode(),
                                h.getRoomCode(), h.getCheckIn(), h.getCheckOut(), h.getRooms(), h.getNightlyRate(),
                                h.getTotal(), h.getConfirmation(), h.getStatus()))
                        .toList(),
                pnr.getCarSegments().stream()
                        .map(c -> new PnrView.CarLine(c.getId(), c.getVendorCode(), c.getLocationCode(), c.getSippCode(),
                                c.getSampleModel(), c.getPickupDate(), c.getReturnDate(), c.getDailyRate(), c.getTotal(),
                                c.getConfirmation(), c.getStatus()))
                        .toList(),
                air, hotelTotal, carTotal, grand, "USD",
                pnr.getTickets().stream()
                        .map(t -> new PnrView.TicketLine(t.getNumber(), t.getPassengerName(), t.getFare(), t.getTaxes(),
                                t.getTotal(), t.getFormOfPayment(), t.getIssuedAt(), t.getStatus(), t.getRefundAmount()))
                        .toList(),
                pnr.getHistory().stream()
                        .map(e -> new PnrView.HistoryLine(e.getAt(), e.getActor(), e.getAction(), e.getDetail()))
                        .toList());
    }

    private static PnrView.Summary summary(Pnr pnr) {
        List<AirSegment> air = pnr.getAirSegments();
        String itinerary = air.isEmpty()
                ? pnr.getHotelSegments().stream().map(HotelSegment::getCityCode).findFirst()
                        .map(city -> "Hotel " + city)
                        .orElse(pnr.getCarSegments().isEmpty() ? "" : "Car " + pnr.getCarSegments().getFirst().getLocationCode())
                : air.getFirst().getOrigin() + air.stream().map(AirSegment::getDestination).collect(Collectors.joining("-", "-", ""));
        return new PnrView.Summary(pnr.getRecordLocator(), pnr.getStatus(),
                pnr.getPassengers().isEmpty() ? "" : pnr.getPassengers().getFirst().displayName(),
                pnr.getPassengers().size(), itinerary, air.isEmpty() ? null : air.getFirst().getDeparture(),
                pnr.getCreatedAt(), pnr.getTicketingDeadline(), pnr.getCreatedBy());
    }

    // ---------------------------------------------------------------- helpers

    private static Map<PassengerType, Integer> countByType(List<PassengerInput> passengers) {
        Map<PassengerType, Integer> counts = new EnumMap<>(PassengerType.class);
        for (PassengerType type : PassengerType.values()) {
            counts.put(type, 0);
        }
        passengers.forEach(p -> counts.merge(p.type(), 1, Integer::sum));
        return counts;
    }

    private static int nights(LocalDate checkIn, LocalDate checkOut) {
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (nights < 1 || nights > 30) {
            throw new BadRequestException("A hotel stay must be 1 to 30 nights");
        }
        return (int) nights;
    }

    private static <T> T find(List<T> items, Function<T, Long> id, Long wanted,
                              Predicate<T> active) {
        return items.stream()
                .filter(item -> id.apply(item).equals(wanted))
                .filter(active)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("No active segment " + wanted));
    }
}
