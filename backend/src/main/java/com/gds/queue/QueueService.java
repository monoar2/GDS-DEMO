package com.gds.queue;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gds.common.NotFoundException;
import com.gds.security.CurrentAgent;

@Service
public class QueueService {

    private static final int PAGE = 200;

    private final QueueEntryRepository entries;

    public QueueService(QueueEntryRepository entries) {
        this.entries = entries;
    }

    @Transactional
    public void place(Long agencyId, QueueName queue, Long pnrId, String recordLocator, String remark) {
        entries.save(new QueueEntry(agencyId, queue, pnrId, recordLocator, remark));
    }

    @Transactional
    public void remove(Long pnrId, QueueName queue) {
        entries.removePnr(pnrId, queue);
    }

    @Transactional(readOnly = true)
    public Map<QueueName, Long> counts(CurrentAgent agent) {
        Map<QueueName, Long> counts = new EnumMap<>(QueueName.class);
        for (QueueName queue : QueueName.values()) {
            counts.put(queue, 0L);
        }
        for (Object[] row : entries.countByQueue(agent.isAdmin() ? null : agent.agencyId())) {
            counts.put((QueueName) row[0], (Long) row[1]);
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public List<QueueEntry> list(CurrentAgent agent, QueueName queue) {
        return agent.isAdmin()
                ? entries.findByQueueOrderByCreatedAt(queue, PageRequest.of(0, PAGE))
                : entries.findByAgencyIdAndQueueOrderByCreatedAt(agent.agencyId(), queue, PageRequest.of(0, PAGE));
    }

    /** Takes one item off a queue once an agent has worked it. */
    @Transactional
    public void work(CurrentAgent agent, Long entryId) {
        QueueEntry entry = entries.findById(entryId)
                .filter(e -> agent.canAccessAgency(e.getAgencyId()))
                .orElseThrow(() -> new NotFoundException("No queue item " + entryId));
        entries.delete(entry);
    }
}
