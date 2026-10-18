package com.gds.queue;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QueueEntryRepository extends JpaRepository<QueueEntry, Long> {

    List<QueueEntry> findByAgencyIdAndQueueOrderByCreatedAt(Long agencyId, QueueName queue, Pageable page);

    List<QueueEntry> findByQueueOrderByCreatedAt(QueueName queue, Pageable page);

    @Modifying
    @Query("delete from QueueEntry e where e.pnrId = :pnrId and e.queue = :queue")
    int removePnr(@Param("pnrId") Long pnrId, @Param("queue") QueueName queue);

    @Query("select e.queue, count(e) from QueueEntry e where (:agencyId is null or e.agencyId = :agencyId) group by e.queue")
    List<Object[]> countByQueue(@Param("agencyId") Long agencyId);
}
