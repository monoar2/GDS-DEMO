package com.gds.queue;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "queue_entry", indexes = {
        @Index(name = "ix_queue_agency", columnList = "agency_id,queue,created_at"),
        @Index(name = "ix_queue_pnr", columnList = "pnr_id,queue")
})
public class QueueEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "queue_entry_seq")
    @SequenceGenerator(name = "queue_entry_seq", sequenceName = "queue_entry_seq", allocationSize = 50)
    private Long id;

    @Column(name = "agency_id", nullable = false)
    private Long agencyId;

    @Enumerated(EnumType.STRING)
    @Column(length = 12, nullable = false)
    private QueueName queue;

    @Column(name = "pnr_id", nullable = false)
    private Long pnrId;

    @Column(length = 6, nullable = false)
    private String recordLocator;

    @Column(length = 300)
    private String remark;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected QueueEntry() {
    }

    public QueueEntry(Long agencyId, QueueName queue, Long pnrId, String recordLocator, String remark) {
        this.agencyId = agencyId;
        this.queue = queue;
        this.pnrId = pnrId;
        this.recordLocator = recordLocator;
        this.remark = remark;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getAgencyId() { return agencyId; }
    public QueueName getQueue() { return queue; }
    public Long getPnrId() { return pnrId; }
    public String getRecordLocator() { return recordLocator; }
    public String getRemark() { return remark; }
    public Instant getCreatedAt() { return createdAt; }
}
