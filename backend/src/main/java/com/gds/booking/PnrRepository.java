package com.gds.booking;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PnrRepository extends JpaRepository<Pnr, Long> {

    Optional<Pnr> findByRecordLocator(String recordLocator);

    boolean existsByRecordLocator(String recordLocator);

    @Query("select p.id from Pnr p where p.status = com.gds.booking.PnrStatus.HELD and p.ticketingDeadline < :now order by p.ticketingDeadline")
    List<Long> findExpiredHolds(@Param("now") Instant now, Pageable page);

    @Query("""
            select p from Pnr p
            where (:agencyId is null or p.agencyId = :agencyId)
              and (:status is null or p.status = :status)
              and (:lastName is null or exists (
                    select 1 from Pnr q join q.passengers pax where q = p and pax.lastName = :lastName))
            order by p.createdAt desc
            """)
    Page<Pnr> search(@Param("agencyId") Long agencyId, @Param("status") PnrStatus status,
                     @Param("lastName") String lastName, Pageable page);
}
