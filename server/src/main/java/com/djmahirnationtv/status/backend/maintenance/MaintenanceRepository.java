package com.djmahirnationtv.status.backend.maintenance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MaintenanceRepository extends JpaRepository<Maintenance, Long> {
    Optional<Maintenance> findByIdAndOwnerId(Long id, Long ownerId);
    Page<Maintenance> findByOwnerId(Long ownerId, Pageable page);
    List<Maintenance> findByMonitorsId(Long monitorId);

    @Query("""
            select count(maintenance) > 0 from Maintenance maintenance
            join maintenance.monitors monitor
            where monitor.id = :monitorId and monitor.ownerId = maintenance.ownerId
            and maintenance.cancelled = false
            and maintenance.startsAt <= :now and maintenance.endsAt > :now
            """)
    boolean isActive(Long monitorId, Instant now);

    @Query("""
            select distinct maintenance from Maintenance maintenance
            join maintenance.monitors monitor
            where maintenance.ownerId = :ownerId and monitor.ownerId = :ownerId
            and maintenance.published = true and monitor.id in :monitorIds
            and maintenance.endsAt > :now
            order by maintenance.startsAt, maintenance.id
            """)
    List<Maintenance> publishedForPage(Long ownerId, Collection<Long> monitorIds, Instant now, Pageable page);
}
