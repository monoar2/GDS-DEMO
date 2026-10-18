package com.gds.booking;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Sweeps held PNRs past their ticketing deadline and gives their seats, rooms and cars back. */
@Component
public class HoldExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryJob.class);
    private static final int BATCH = 500;

    private final PnrRepository pnrs;
    private final PnrService service;

    public HoldExpiryJob(PnrRepository pnrs, PnrService service) {
        this.pnrs = pnrs;
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${gds.booking.expiry-check}")
    public int expireHolds() {
        List<Long> due = pnrs.findExpiredHolds(Instant.now(), PageRequest.of(0, BATCH));
        int expired = 0;
        for (Long id : due) {
            try {
                if (service.expire(id)) {
                    expired++;
                }
            } catch (RuntimeException e) {
                // e.g. an agent ticketed it at the same moment; the next sweep will look again
                log.debug("Could not expire PNR {}: {}", id, e.getMessage());
            }
        }
        if (expired > 0) {
            log.info("Expired {} held PNR(s)", expired);
        }
        return expired;
    }
}
