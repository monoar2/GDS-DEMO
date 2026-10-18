package com.gds.booking;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.gds.booking.PnrRequests.ContactUpdate;
import com.gds.booking.PnrRequests.CreatePnr;
import com.gds.booking.PnrRequests.SegmentType;
import com.gds.booking.PnrRequests.Segments;
import com.gds.booking.PnrRequests.TicketRequest;
import com.gds.security.CurrentAgent;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/pnrs")
public class PnrController {

    private final PnrService pnrs;

    public PnrController(PnrService pnrs) {
        this.pnrs = pnrs;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PnrView create(@Valid @RequestBody CreatePnr request, @AuthenticationPrincipal Jwt jwt) {
        return pnrs.create(request, CurrentAgent.from(jwt));
    }

    @GetMapping
    public Page<PnrView.Summary> search(@RequestParam(required = false) PnrStatus status,
                                        @RequestParam(required = false) String lastName,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size,
                                        @AuthenticationPrincipal Jwt jwt) {
        return pnrs.search(CurrentAgent.from(jwt), status, lastName, page, size);
    }

    @GetMapping("/{locator}")
    public PnrView retrieve(@PathVariable String locator, @AuthenticationPrincipal Jwt jwt) {
        return pnrs.retrieve(locator, CurrentAgent.from(jwt));
    }

    @PostMapping("/{locator}/segments")
    public PnrView addSegments(@PathVariable String locator, @Valid @RequestBody Segments segments,
                               @AuthenticationPrincipal Jwt jwt) {
        return pnrs.addSegments(locator, segments, CurrentAgent.from(jwt));
    }

    @DeleteMapping("/{locator}/segments/{type}/{segmentId}")
    public PnrView cancelSegment(@PathVariable String locator, @PathVariable SegmentType type, @PathVariable Long segmentId,
                                 @AuthenticationPrincipal Jwt jwt) {
        return pnrs.cancelSegment(locator, type, segmentId, CurrentAgent.from(jwt));
    }

    @PatchMapping("/{locator}/contact")
    public PnrView updateContact(@PathVariable String locator, @Valid @RequestBody ContactUpdate update,
                                 @AuthenticationPrincipal Jwt jwt) {
        return pnrs.updateContact(locator, update, CurrentAgent.from(jwt));
    }

    @PostMapping("/{locator}/ticket")
    public PnrView ticket(@PathVariable String locator, @Valid @RequestBody TicketRequest request,
                          @AuthenticationPrincipal Jwt jwt) {
        return pnrs.ticket(locator, request, CurrentAgent.from(jwt));
    }

    @PostMapping("/{locator}/cancel")
    public PnrView cancel(@PathVariable String locator, @AuthenticationPrincipal Jwt jwt) {
        return pnrs.cancel(locator, CurrentAgent.from(jwt));
    }
}
