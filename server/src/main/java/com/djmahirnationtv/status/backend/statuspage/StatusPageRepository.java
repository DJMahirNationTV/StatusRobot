package com.djmahirnationtv.status.backend.statuspage;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface StatusPageRepository extends JpaRepository<StatusPage, Long> {
    List<StatusPage> findByOwnerIdOrderByIdDesc(Long ownerId);
    Optional<StatusPage> findBySlug(String slug);
    Optional<StatusPage> findByDefaultSlot(Integer slot);
    boolean existsBySlugAndIdNot(String slug, Long id);
    List<StatusPage> findByMonitorsId(Long monitorId);
}
