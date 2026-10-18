package com.gds.queue;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.gds.booking.Pnr;
import com.gds.booking.PnrService;
import com.gds.security.CurrentAgent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/queues")
public class QueueController {

    private final QueueService queues;
    private final PnrService pnrs;

    public QueueController(QueueService queues, PnrService pnrs) {
        this.queues = queues;
        this.pnrs = pnrs;
    }

    @GetMapping
    public Map<QueueName, Long> counts(@AuthenticationPrincipal Jwt jwt) {
        return queues.counts(CurrentAgent.from(jwt));
    }

    @GetMapping("/{queue}")
    public List<QueueItem> list(@PathVariable QueueName queue, @AuthenticationPrincipal Jwt jwt) {
        return queues.list(CurrentAgent.from(jwt), queue).stream().map(QueueItem::of).toList();
    }

    /** Places a PNR on a queue by hand, e.g. GENERAL with a follow-up note. */
    @PostMapping("/{queue}")
    @ResponseStatus(HttpStatus.CREATED)
    public void place(@PathVariable QueueName queue, @Valid @RequestBody Placement placement, @AuthenticationPrincipal Jwt jwt) {
        Pnr pnr = pnrs.load(placement.recordLocator(), CurrentAgent.from(jwt));
        queues.place(pnr.getAgencyId(), queue, pnr.getId(), pnr.getRecordLocator(), placement.remark());
    }

    @DeleteMapping("/items/{entryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void work(@PathVariable Long entryId, @AuthenticationPrincipal Jwt jwt) {
        queues.work(CurrentAgent.from(jwt), entryId);
    }

    public record Placement(@NotBlank String recordLocator, @Size(max = 300) String remark) {
    }

    public record QueueItem(Long id, QueueName queue, String recordLocator, String remark, Instant createdAt) {
        static QueueItem of(QueueEntry e) {
            return new QueueItem(e.getId(), e.getQueue(), e.getRecordLocator(), e.getRemark(), e.getCreatedAt());
        }
    }
}
