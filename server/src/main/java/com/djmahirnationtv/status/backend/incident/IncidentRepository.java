package com.djmahirnationtv.status.backend.incident;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {
    Optional<Incident> findByOpenMonitorId(Long monitorId);
}
