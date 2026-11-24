package com.djmahirnationtv.status.backend.incident;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import java.util.List;
import java.util.Collection;
import org.springframework.data.jpa.repository.Query;

public interface IncidentRepository extends JpaRepository<Incident, Long> {
    Optional<Incident> findByOpenMonitorId(Long monitorId);
    Optional<Incident> findByIdAndOwnerId(Long id, Long ownerId);
    Page<Incident> findByOwnerId(Long ownerId, Pageable pageable);
    Page<Incident> findByOwnerIdAndResolvedAtIsNull(Long ownerId, Pageable pageable);
    Page<Incident> findByOwnerIdAndResolvedAtIsNotNull(Long ownerId, Pageable pageable);
    void deleteByMonitorId(Long monitorId);
/* [SQL STATEMENT]
SELECT *
FROM incident i
WHERE i.owner_id = :owner_id
  AND i.monitor_id IN (:monitor_ids)
  AND i.is_published = TRUE
ORDER BY
  CASE WHEN i.resolved_at IS NULL THEN 0 ELSE 1 END,
  i.started_at DESC,
  i.id DESC;
*/

    @Query("""
            select i from Incident i where i.ownerId = :ownerId and i.monitorId in :monitorIds and i.published = true
            order by case when i.resolvedAt is null then 0 else 1 end, i.startedAt desc, i.id desc
            """)
    List<Incident> publishedForPage(Long ownerId, Collection<Long> monitorIds, Pageable paging);
}
